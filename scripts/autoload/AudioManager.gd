extends Node
# ============================================================
# AudioManager.gd — 音频管理单例 (Autoload)
# 职责：BGM 层级播放、SFX 播放、连击音乐变奏
# 设计：使用多个 AudioStreamPlayer 做 BGM 层叠
#       Tween 控制音量淡入淡出（无需 AudioStreamInteractive）
# ============================================================

# ── 信号 ──────────────────────────────────────────────────
signal bgm_layer_changed(layer_index)

# ── BGM 层级设计 ──────────────────────────────────────────
# 层0: 基础节奏（始终播放）
# 层1: 旋律层（combo >= 2 时淡入）
# 层2: 高潮层（combo >= 4 时淡入）
# 层3: 疯狂层（combo >= 6 时淡入，加入合成器）
const BGM_LAYER_COUNT := 4
const BGM_FADE_TIME := 0.5           # 淡入淡出时间（秒）
const BGM_COMBO_THRESHOLDS := [0, 2, 4, 6]  # 各层激活的连击阈值

# ── SFX 类型 ─────────────────────────────────────────────
# 使用字符串键而非枚举，方便扩展
const SFX = {
	"swap": "res://assets/audio/sfx_swap.wav",
	"match3": "res://assets/audio/sfx_match3.wav",
	"match4": "res://assets/audio/sfx_match4.wav",
	"match5": "res://assets/audio/sfx_match5.wav",
	"combo": "res://assets/audio/sfx_combo.wav",
	"special_create": "res://assets/audio/sfx_special_create.wav",
	"special_explode": "res://assets/audio/sfx_special_explode.wav",
	"level_complete": "res://assets/audio/sfx_level_complete.wav",
	"level_fail": "res://assets/audio/sfx_level_fail.wav",
	"button_click": "res://assets/audio/sfx_button_click.wav",
}

# ── 节点引用 ──────────────────────────────────────────────
var bgm_players := []       # Array of AudioStreamPlayer
var sfx_pool := []           # SFX 播放器对象池
var tween: Tween             # 用于音量过渡

const SFX_POOL_SIZE := 8    # SFX 同时播放上限（低端友好）
var current_bgm_layer := 0   # 当前激活的最高 BGM 层


func _ready() -> void:
	pause_mode = PAUSE_MODE_PROCESS
	_setup_bgm_players()
	_setup_sfx_pool()
	_setup_tween()
	# 监听 GameManager 连击变化
	GameManager.connect("combo_changed", self, "_on_combo_changed")
	GameManager.connect("combo_ended", self, "_on_combo_ended")
	print("[AudioManager] 初始化完成，BGM %d 层，SFX 池 %d" % [BGM_LAYER_COUNT, SFX_POOL_SIZE])


# ── 初始化 ────────────────────────────────────────────────
func _setup_bgm_players() -> void:
	"""创建 BGM 层级播放器"""
	for i in range(BGM_LAYER_COUNT):
		var player := AudioStreamPlayer.new()
		player.name = "BGM_Layer_%d" % i
		player.bus = "Music"
		player.volume_db = -80.0 if i > 0 else 0.0  # 只有基础层有声音
		add_child(player)
		bgm_players.append(player)


func _setup_sfx_pool() -> void:
	"""创建 SFX 对象池（避免运行时创建节点）"""
	for i in range(SFX_POOL_SIZE):
		var player := AudioStreamPlayer.new()
		player.name = "SFX_%d" % i
		player.bus = "SFX"
		add_child(player)
		sfx_pool.append(player)


func _setup_tween() -> void:
	"""创建 Tween 节点用于音量过渡"""
	tween = Tween.new()
	tween.name = "AudioTween"
	add_child(tween)


# ── BGM 控制 ──────────────────────────────────────────────
func play_bgm(layer_streams: Array) -> void:
	"""
	开始播放 BGM。传入各层的 AudioStream 数组。
	layer_streams: [AudioStream, AudioStream, ...]
	所有层同步播放，但只有层0 有音量，其余静音待激活。
	"""
	for i in range(min(layer_streams.size(), BGM_LAYER_COUNT)):
		var player: AudioStreamPlayer = bgm_players[i]
		player.stream = layer_streams[i]
		player.volume_db = 0.0 if i == 0 else -80.0
		player.play()
	current_bgm_layer = 0


func stop_bgm(fade_out := true) -> void:
	"""停止所有 BGM 层"""
	for player in bgm_players:
		if fade_out:
			tween.interpolate_property(
				player, "volume_db",
				player.volume_db, -80.0,
				BGM_FADE_TIME,
				Tween.TRANS_EXPO, Tween.EASE_IN
			)
		else:
			player.stop()
	if fade_out:
		tween.start()
		yield(tween, "tween_all_completed")
		for player in bgm_players:
			player.stop()


func _activate_bgm_layer(layer: int) -> void:
	"""淡入指定 BGM 层"""
	if layer < 0 or layer >= BGM_LAYER_COUNT:
		return
	if layer <= current_bgm_layer:
		return
	var player: AudioStreamPlayer = bgm_players[layer]
	tween.interpolate_property(
		player, "volume_db",
		player.volume_db, 0.0,
		BGM_FADE_TIME,
		Tween.TRANS_CUBIC, Tween.EASE_OUT
	)
	tween.start()
	current_bgm_layer = layer
	emit_signal("bgm_layer_changed", layer)
	print("[AudioManager] BGM 层 %d 激活" % layer)


func _deactivate_bgm_layers_above(layer: int) -> void:
	"""淡出高于指定层的所有 BGM 层"""
	for i in range(layer + 1, BGM_LAYER_COUNT):
		var player: AudioStreamPlayer = bgm_players[i]
		if player.volume_db > -79.0:
			tween.interpolate_property(
				player, "volume_db",
				player.volume_db, -80.0,
				BGM_FADE_TIME * 2.0,  # 淡出稍慢，更自然
				Tween.TRANS_CUBIC, Tween.EASE_IN
			)
	tween.start()
	current_bgm_layer = layer


# ── SFX 播放 ─────────────────────────────────────────────
func play_sfx(sfx_key: String, pitch_variation := 0.0) -> void:
	"""
	播放音效。自动从对象池取可用播放器。
	sfx_key: SFX 字典的键名
	pitch_variation: 随机音调偏移量（0.0 = 不偏移）
	"""
	if not SFX.has(sfx_key):
		print("[AudioManager] 未知 SFX: %s" % sfx_key)
		return

	var player := _get_available_sfx_player()
	if player == null:
		return  # 所有播放器都在使用中，跳过（低端保护）

	var stream = load(SFX[sfx_key])
	if stream == null:
		# 音频文件不存在时静默跳过（开发阶段友好）
		return

	player.stream = stream
	if pitch_variation > 0.0:
		player.pitch_scale = 1.0 + rand_range(-pitch_variation, pitch_variation)
	else:
		player.pitch_scale = 1.0
	player.play()


func play_combo_sfx(combo: int) -> void:
	"""根据连击数播放递增音高的音效（爽感核心！）"""
	var player := _get_available_sfx_player()
	if player == null:
		return

	var stream = load(SFX.get("combo", ""))
	if stream == null:
		return

	player.stream = stream
	# 连击越高音调越高，上限 2.0（高八度）
	player.pitch_scale = 1.0 + clamp(combo * 0.1, 0.0, 1.0)
	player.play()


func _get_available_sfx_player() -> AudioStreamPlayer:
	"""从对象池获取空闲的 SFX 播放器"""
	for player in sfx_pool:
		if not player.playing:
			return player
	return null  # 全部占用


# ── 连击响应 ──────────────────────────────────────────────
func _on_combo_changed(combo: int) -> void:
	"""连击变化时更新 BGM 层级"""
	for i in range(BGM_LAYER_COUNT - 1, -1, -1):
		if combo >= BGM_COMBO_THRESHOLDS[i]:
			_activate_bgm_layer(i)
			break
	# 播放连击音效
	if combo > 1:
		play_combo_sfx(combo)


func _on_combo_ended() -> void:
	"""连击结束，逐渐回到基础 BGM"""
	_deactivate_bgm_layers_above(0)

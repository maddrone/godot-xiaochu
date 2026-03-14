extends Node
# ============================================================
# GameManager.gd — 全局游戏状态管理单例 (Autoload)
# 职责：分数、连击、游戏状态、全局事件总线
# ============================================================

# ── 信号（事件总线，解耦各模块） ──────────────────────────
signal score_changed(new_score)          # 分数变化
signal combo_changed(combo_count)        # 连击数变化
signal combo_ended()                     # 连击结束
signal moves_changed(remaining)          # 剩余步数变化
signal game_state_changed(new_state)     # 游戏状态切换
signal level_completed()                 # 关卡通关
signal level_failed()                    # 关卡失败
signal gems_matched(match_data)          # 宝石消除事件 {type, count, position, is_special}

# ── 游戏状态枚举 ───────────────────────────────────────────
enum GameState {
	LOADING,      # 加载中
	READY,        # 准备就绪（可操作）
	SWAPPING,     # 正在交换
	MATCHING,     # 正在检测匹配
	ELIMINATING,  # 消除动画中
	FALLING,      # 下落填充中
	PAUSED,       # 暂停
	GAME_OVER     # 游戏结束
}

# ── 宝石类型枚举 ───────────────────────────────────────────
enum GemType {
	RED,
	ORANGE,
	YELLOW,
	GREEN,
	BLUE,
	PURPLE,
	# 以下为特殊宝石
	STRIPED_H,    # 横条纹
	STRIPED_V,    # 竖条纹
	BOMB,         # 炸弹（3x3 范围爆炸）
	RAINBOW       # 彩虹（消除同色）
}

# 普通宝石数量（用于随机生成）
const NORMAL_GEM_COUNT := 6

# ── 棋盘配置 ─────────────────────────────────────────────
const BOARD_COLS := 8        # 列数
const BOARD_ROWS := 8        # 行数
const CELL_SIZE := 80        # 单元格像素大小
const BOARD_OFFSET := Vector2(40, 300)  # 棋盘左上角偏移（适配720x1280）

# ── 游戏数据 ─────────────────────────────────────────────
var current_state: int = GameState.LOADING setget set_state
var score: int = 0 setget set_score
var combo_count: int = 0 setget set_combo
var max_combo: int = 0             # 本局最大连击
var moves_remaining: int = 30 setget set_moves
var current_level: int = 1

# ── 连击倍率表（连击越高，得分倍率越大，爽感飙升） ──────
# combo_count: 1=x1, 2=x1.5, 3=x2, 4=x3, 5+=x5
const COMBO_MULTIPLIERS := [1.0, 1.0, 1.5, 2.0, 3.0, 5.0]

# ── 粒子强度随连击缩放（但设上限防爆帧） ────────────────
const PARTICLE_SCALE_MIN := 1.0
const PARTICLE_SCALE_MAX := 3.0     # 最多 3 倍粒子
const SCREEN_SHAKE_BASE := 2.0      # 基础屏幕抖动
const SCREEN_SHAKE_MAX := 10.0      # 最大抖动幅度


func _ready() -> void:
	pause_mode = PAUSE_MODE_PROCESS  # 暂停时仍可处理
	print("[GameManager] 初始化完成")


# ── 状态管理 ──────────────────────────────────────────────
func set_state(new_state: int) -> void:
	if current_state == new_state:
		return
	current_state = new_state
	emit_signal("game_state_changed", new_state)


func is_input_allowed() -> bool:
	"""只有 READY 状态才允许玩家操作"""
	return current_state == GameState.READY


# ── 分数系统 ──────────────────────────────────────────────
func set_score(value: int) -> void:
	score = value
	emit_signal("score_changed", score)


func add_score(base_points: int) -> void:
	"""根据连击倍率计算最终得分"""
	var multiplier := get_combo_multiplier()
	var final_points := int(base_points * multiplier)
	self.score += final_points  # 触发 setter


# ── 连击系统 ──────────────────────────────────────────────
func set_combo(value: int) -> void:
	combo_count = value
	if combo_count > max_combo:
		max_combo = combo_count
	emit_signal("combo_changed", combo_count)


func increment_combo() -> void:
	"""连击 +1"""
	self.combo_count += 1


func reset_combo() -> void:
	"""连击归零"""
	if combo_count > 0:
		emit_signal("combo_ended")
	self.combo_count = 0


func get_combo_multiplier() -> float:
	"""获取当前连击倍率"""
	var index := int(clamp(combo_count, 0, COMBO_MULTIPLIERS.size() - 1))
	return COMBO_MULTIPLIERS[index]


func get_particle_intensity() -> float:
	"""获取粒子强度缩放值（1.0 ~ 3.0）"""
	var t := clamp(float(combo_count) / 5.0, 0.0, 1.0)
	return lerp(PARTICLE_SCALE_MIN, PARTICLE_SCALE_MAX, t)


func get_screen_shake_intensity() -> float:
	"""获取屏幕抖动强度"""
	var t := clamp(float(combo_count) / 5.0, 0.0, 1.0)
	return lerp(SCREEN_SHAKE_BASE, SCREEN_SHAKE_MAX, t)


# ── 步数管理 ──────────────────────────────────────────────
func set_moves(value: int) -> void:
	moves_remaining = value
	emit_signal("moves_changed", moves_remaining)
	if moves_remaining <= 0:
		_check_game_over()


func use_move() -> void:
	"""消耗一步"""
	self.moves_remaining -= 1


# ── 关卡流程 ──────────────────────────────────────────────
func start_level(level_id: int) -> void:
	"""初始化新关卡"""
	current_level = level_id
	self.score = 0
	self.combo_count = 0
	max_combo = 0
	# moves_remaining 由 LevelManager 根据关卡数据设置
	self.current_state = GameState.LOADING
	print("[GameManager] 开始关卡 %d" % level_id)


func _check_game_over() -> void:
	"""检查是否游戏结束"""
	if moves_remaining <= 0:
		# TODO: 检查是否达成关卡目标
		self.current_state = GameState.GAME_OVER
		emit_signal("level_failed")


func complete_level() -> void:
	"""关卡通关"""
	self.current_state = GameState.GAME_OVER
	emit_signal("level_completed")


# ── 工具函数 ──────────────────────────────────────────────
func grid_to_pixel(col: int, row: int) -> Vector2:
	"""网格坐标 → 像素坐标（单元格中心）"""
	return BOARD_OFFSET + Vector2(
		col * CELL_SIZE + CELL_SIZE * 0.5,
		row * CELL_SIZE + CELL_SIZE * 0.5
	)


func pixel_to_grid(pixel_pos: Vector2) -> Vector2:
	"""像素坐标 → 网格坐标（可能越界，需要调用方检查）"""
	var local := pixel_pos - BOARD_OFFSET
	return Vector2(
		int(local.x / CELL_SIZE),
		int(local.y / CELL_SIZE)
	)


func is_valid_cell(col: int, row: int) -> bool:
	"""检查网格坐标是否在棋盘内"""
	return col >= 0 and col < BOARD_COLS and row >= 0 and row < BOARD_ROWS

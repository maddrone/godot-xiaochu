extends Node
# ============================================================
# LevelManager.gd — 关卡管理单例 (Autoload)
# 职责：关卡数据加载、目标检测、关卡解锁、星级评定
# 数据驱动设计：关卡配置存储在 JSON 文件中
# ============================================================

# ── 信号 ──────────────────────────────────────────────────
signal level_loaded(level_data)
signal objective_updated(objective_id, current, target)
signal star_earned(star_count)

# ── 关卡目标类型 ──────────────────────────────────────────
enum ObjectiveType {
	SCORE,          # 达到目标分数
	COLLECT_GEM,    # 收集指定颜色宝石
	CLEAR_BLOCKER,  # 清除障碍物
	COMBO_REACH,    # 达到指定连击数
}

# ── 关卡数据路径 ──────────────────────────────────────────
const LEVELS_DATA_PATH := "res://data/levels.json"
const MAX_STARS := 3

# ── 数据 ─────────────────────────────────────────────────
var all_levels := []             # 所有关卡配置
var current_level_data := {}     # 当前关卡数据
var objective_progress := {}     # 目标进度 {objective_id: current_value}
var unlocked_levels := [1]       # 已解锁关卡列表
var level_stars := {}            # {level_id: star_count}


func _ready() -> void:
	pause_mode = PAUSE_MODE_PROCESS
	_load_levels_data()
	# 监听游戏事件来追踪目标进度
	GameManager.connect("gems_matched", self, "_on_gems_matched")
	GameManager.connect("score_changed", self, "_on_score_changed")
	GameManager.connect("combo_changed", self, "_on_combo_changed")
	print("[LevelManager] 初始化完成，共 %d 关" % all_levels.size())


# ── 关卡数据加载 ──────────────────────────────────────────
func _load_levels_data() -> void:
	"""从 JSON 加载所有关卡配置"""
	var file := File.new()
	if not file.file_exists(LEVELS_DATA_PATH):
		print("[LevelManager] 关卡数据文件不存在，使用默认关卡")
		_create_default_levels()
		return

	if file.open(LEVELS_DATA_PATH, File.READ) != OK:
		print("[LevelManager] 无法打开关卡数据文件")
		_create_default_levels()
		return

	var json_text := file.get_as_text()
	file.close()

	var parse_result := JSON.parse(json_text)
	if parse_result.error != OK:
		print("[LevelManager] JSON 解析错误: %s" % parse_result.error_string)
		_create_default_levels()
		return

	all_levels = parse_result.result
	print("[LevelManager] 成功加载 %d 关" % all_levels.size())


func _create_default_levels() -> void:
	"""创建默认关卡数据（开发/测试用）"""
	all_levels = [
		{
			"id": 1,
			"name": "初次相遇",
			"moves": 30,
			"gem_types": 5,          # 使用前 5 种颜色
			"objectives": [
				{"type": "SCORE", "target": 1000}
			],
			"star_thresholds": [1000, 2000, 3500],
			"board_layout": null,    # null = 标准 8x8
			"special_rules": [],
			"bg_theme": "meadow"
		},
		{
			"id": 2,
			"name": "连击初体验",
			"moves": 25,
			"gem_types": 5,
			"objectives": [
				{"type": "SCORE", "target": 2000},
				{"type": "COMBO_REACH", "target": 3}
			],
			"star_thresholds": [2000, 3500, 5000],
			"board_layout": null,
			"special_rules": [],
			"bg_theme": "meadow"
		},
		{
			"id": 3,
			"name": "色彩收集",
			"moves": 28,
			"gem_types": 6,
			"objectives": [
				{"type": "COLLECT_GEM", "gem_type": "RED", "target": 20},
				{"type": "COLLECT_GEM", "gem_type": "BLUE", "target": 20}
			],
			"star_thresholds": [2500, 4000, 6000],
			"board_layout": null,
			"special_rules": [],
			"bg_theme": "forest"
		},
		{
			"id": 4,
			"name": "炸弹派对",
			"moves": 22,
			"gem_types": 6,
			"objectives": [
				{"type": "SCORE", "target": 5000}
			],
			"star_thresholds": [5000, 8000, 12000],
			"board_layout": null,
			"special_rules": ["more_specials"],
			"bg_theme": "volcano"
		},
		{
			"id": 5,
			"name": "极限挑战",
			"moves": 20,
			"gem_types": 6,
			"objectives": [
				{"type": "SCORE", "target": 8000},
				{"type": "COMBO_REACH", "target": 5}
			],
			"star_thresholds": [8000, 12000, 18000],
			"board_layout": null,
			"special_rules": ["more_specials"],
			"bg_theme": "space"
		},
	]


# ── 关卡流程控制 ──────────────────────────────────────────
func load_level(level_id: int) -> Dictionary:
	"""加载指定关卡，返回关卡数据"""
	for level in all_levels:
		if level.get("id") == level_id:
			current_level_data = level.duplicate(true)
			objective_progress.clear()
			_init_objectives()
			GameManager.start_level(level_id)
			GameManager.moves_remaining = level.get("moves", 30)
			emit_signal("level_loaded", current_level_data)
			return current_level_data

	print("[LevelManager] 关卡 %d 不存在" % level_id)
	return {}


func _init_objectives() -> void:
	"""初始化关卡目标进度"""
	var objectives = current_level_data.get("objectives", [])
	for i in range(objectives.size()):
		objective_progress[i] = 0


func get_gem_type_count() -> int:
	"""获取当前关卡使用的宝石颜色数量"""
	return current_level_data.get("gem_types", GameManager.NORMAL_GEM_COUNT)


func has_special_rule(rule: String) -> bool:
	"""检查当前关卡是否有指定特殊规则"""
	return rule in current_level_data.get("special_rules", [])


# ── 目标追踪 ──────────────────────────────────────────────
func _on_gems_matched(match_data: Dictionary) -> void:
	"""宝石消除时更新收集目标"""
	var objectives = current_level_data.get("objectives", [])
	for i in range(objectives.size()):
		var obj = objectives[i]
		if obj.get("type") == "COLLECT_GEM":
			var gem_type_name = GameManager.GemType.keys()[match_data.get("type", 0)]
			if gem_type_name == obj.get("gem_type", ""):
				objective_progress[i] += match_data.get("count", 1)
				emit_signal("objective_updated", i,
					objective_progress[i], obj.get("target", 0))
				_check_objectives_complete()


func _on_score_changed(new_score: int) -> void:
	"""分数变化时更新分数目标"""
	var objectives = current_level_data.get("objectives", [])
	for i in range(objectives.size()):
		var obj = objectives[i]
		if obj.get("type") == "SCORE":
			objective_progress[i] = new_score
			emit_signal("objective_updated", i,
				new_score, obj.get("target", 0))
			_check_objectives_complete()


func _on_combo_changed(combo: int) -> void:
	"""连击变化时更新连击目标"""
	var objectives = current_level_data.get("objectives", [])
	for i in range(objectives.size()):
		var obj = objectives[i]
		if obj.get("type") == "COMBO_REACH":
			if combo > objective_progress.get(i, 0):
				objective_progress[i] = combo
				emit_signal("objective_updated", i,
					combo, obj.get("target", 0))
				_check_objectives_complete()


func _check_objectives_complete() -> bool:
	"""检查是否所有目标都已完成"""
	var objectives = current_level_data.get("objectives", [])
	for i in range(objectives.size()):
		var target = objectives[i].get("target", 0)
		if objective_progress.get(i, 0) < target:
			return false
	# 全部目标达成！
	var stars := _calculate_stars()
	emit_signal("star_earned", stars)
	_save_level_result(stars)
	GameManager.complete_level()
	return true


func _calculate_stars() -> int:
	"""根据分数计算星级"""
	var thresholds = current_level_data.get("star_thresholds", [0, 0, 0])
	var stars := 0
	for threshold in thresholds:
		if GameManager.score >= threshold:
			stars += 1
	return stars


func _save_level_result(stars: int) -> void:
	"""保存关卡结果"""
	var level_id = current_level_data.get("id", 0)
	# 只保存更高的星级
	if stars > level_stars.get(level_id, 0):
		level_stars[level_id] = stars
	# 解锁下一关
	var next_id = level_id + 1
	if not next_id in unlocked_levels and next_id <= all_levels.size():
		unlocked_levels.append(next_id)


func is_level_unlocked(level_id: int) -> bool:
	return level_id in unlocked_levels


func get_level_stars(level_id: int) -> int:
	return level_stars.get(level_id, 0)

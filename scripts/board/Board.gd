extends Node2D
# ============================================================
# Board.gd — 棋盘核心逻辑
# 职责：网格管理、宝石生成、输入处理（选中+交换）
# 场景结构：
#   Board (Node2D)
#     ├── GridBackground  — 棋盘背景网格
#     ├── GemsContainer   — 所有宝石的父节点
#     └── Tween           — 交换动画
# ============================================================

# ── 子节点引用 ────────────────────────────────────────────
onready var gems_container: Node2D = $GemsContainer
onready var effects_container: Node2D = $EffectsContainer
onready var tween: Tween = $Tween

# ── 场景预加载 ────────────────────────────────────────────
var gem_scene: PackedScene = preload("res://scenes/gem/Gem.tscn")
var MatchEffect := preload("res://scripts/effects/MatchEffect.gd")
var ComboDisplay := preload("res://scripts/effects/ComboDisplay.gd")
var ScorePopup := preload("res://scripts/effects/ScorePopup.gd")

# ── 棋盘数据（2D 数组，存储 Gem 节点引用） ───────────────
# grid[col][row] = Gem node or null
var grid := []

# ── 输入状态 ──────────────────────────────────────────────
var selected_gem = null          # 当前选中的宝石
var is_processing := false       # 是否正在处理消除链

# ── 滑动检测 ──────────────────────────────────────────────
var touch_start_pos := Vector2.ZERO
var is_touching := false
const SWIPE_THRESHOLD := 20.0    # 滑动最小距离（像素）


func _ready() -> void:
	_draw_grid_background()
	_init_grid()
	_fill_board()
	# 确保初始棋盘没有三连
	_resolve_initial_matches()
	print("[Board] 棋盘初始化完成 %dx%d" % [GameManager.BOARD_COLS, GameManager.BOARD_ROWS])


# ── 棋盘初始化 ────────────────────────────────────────────
func _init_grid() -> void:
	"""初始化空的二维网格数组"""
	grid.clear()
	for col in range(GameManager.BOARD_COLS):
		var column := []
		for row in range(GameManager.BOARD_ROWS):
			column.append(null)
		grid.append(column)


func _fill_board() -> void:
	"""填充整个棋盘（初始生成）"""
	var gem_count := LevelManager.get_gem_type_count()
	for col in range(GameManager.BOARD_COLS):
		for row in range(GameManager.BOARD_ROWS):
			var gem_type := randi() % gem_count
			var gem := _create_gem(gem_type, col, row)
			gem.spawn_animation(randf() * 0.3)  # 随机延迟出场


func _resolve_initial_matches() -> void:
	"""消除初始棋盘上的三连（替换为不同颜色）"""
	var gem_count := LevelManager.get_gem_type_count()
	var changed := true
	var max_iterations := 100  # 防止无限循环

	while changed and max_iterations > 0:
		changed = false
		max_iterations -= 1
		for col in range(GameManager.BOARD_COLS):
			for row in range(GameManager.BOARD_ROWS):
				if _has_match_at(col, row):
					# 替换当前宝石为不同类型
					var old_type: int = grid[col][row].gem_type
					var new_type := old_type
					while new_type == old_type or _would_match(col, row, new_type):
						new_type = randi() % gem_count
					grid[col][row].gem_type = new_type
					grid[col][row].update()  # 触发 _draw() 重绘
					changed = true

	if max_iterations <= 0:
		print("[Board] 警告：初始消除解算超过上限")


func _has_match_at(col: int, row: int) -> bool:
	"""检查指定位置是否形成三连"""
	if grid[col][row] == null:
		return false
	var type: int = grid[col][row].gem_type

	# 水平检查（向左数）
	if col >= 2:
		if grid[col-1][row] != null and grid[col-2][row] != null:
			if grid[col-1][row].gem_type == type and grid[col-2][row].gem_type == type:
				return true

	# 垂直检查（向上数）
	if row >= 2:
		if grid[col][row-1] != null and grid[col][row-2] != null:
			if grid[col][row-1].gem_type == type and grid[col][row-2].gem_type == type:
				return true

	return false


func _would_match(col: int, row: int, type: int) -> bool:
	"""检查如果在指定位置放入指定类型是否会形成三连"""
	# 水平检查
	if col >= 2:
		if grid[col-1][row] != null and grid[col-2][row] != null:
			if grid[col-1][row].gem_type == type and grid[col-2][row].gem_type == type:
				return true
	# 垂直检查
	if row >= 2:
		if grid[col][row-1] != null and grid[col][row-2] != null:
			if grid[col][row-1].gem_type == type and grid[col][row-2].gem_type == type:
				return true
	return false


# ── 宝石创建 ──────────────────────────────────────────────
func _create_gem(type: int, col: int, row: int) -> Node2D:
	"""创建一个宝石并放置到网格中"""
	var gem = gem_scene.instance()
	gems_container.add_child(gem)
	gem.init(type, col, row)
	gem.connect("gem_selected", self, "_on_gem_selected")
	grid[col][row] = gem
	return gem


# ── 棋盘背景 ──────────────────────────────────────────────
func _draw_grid_background() -> void:
	"""绘制棋盘格子背景（交替色）"""
	for col in range(GameManager.BOARD_COLS):
		for row in range(GameManager.BOARD_ROWS):
			var cell := ColorRect.new()
			cell.rect_size = Vector2(GameManager.CELL_SIZE - 2, GameManager.CELL_SIZE - 2)
			cell.rect_position = GameManager.BOARD_OFFSET + Vector2(
				col * GameManager.CELL_SIZE + 1,
				row * GameManager.CELL_SIZE + 1
			)
			# 交替颜色的棋盘格
			if (col + row) % 2 == 0:
				cell.color = Color(0.18, 0.17, 0.25, 0.8)
			else:
				cell.color = Color(0.22, 0.21, 0.30, 0.8)
			$GridBackground.add_child(cell)


# ── 输入处理 ──────────────────────────────────────────────
func _unhandled_input(event: InputEvent) -> void:
	if not GameManager.is_input_allowed() or is_processing:
		return

	# 将视口坐标转换为世界坐标（兼容 Camera2D 偏移/抖动）
	var world_pos := get_canvas_transform().affine_inverse().xform(
		event.position if event is InputEventMouseButton or event is InputEventMouseMotion else Vector2.ZERO
	)

	# 触摸/鼠标按下
	if event is InputEventMouseButton:
		if event.pressed:
			touch_start_pos = world_pos
			is_touching = true
			_handle_touch(world_pos)
		else:
			if is_touching and selected_gem != null:
				_handle_swipe(world_pos)
			is_touching = false

	# 触摸/鼠标拖动（检测滑动方向）
	if event is InputEventMouseMotion and is_touching and selected_gem != null:
		var distance := world_pos.distance_to(touch_start_pos)
		if distance > SWIPE_THRESHOLD:
			_handle_swipe(world_pos)
			is_touching = false


func _handle_touch(pos: Vector2) -> void:
	"""处理点击：选中宝石"""
	var grid_pos := GameManager.pixel_to_grid(pos)
	var col := int(grid_pos.x)
	var row := int(grid_pos.y)

	if not GameManager.is_valid_cell(col, row):
		return
	if grid[col][row] == null:
		return

	var gem = grid[col][row]

	if selected_gem == null:
		# 首次选中
		_select_gem(gem)
	elif selected_gem == gem:
		# 再次点击取消选中
		_deselect_gem()
	else:
		# 点击了另一个宝石，检查是否相邻
		if _are_adjacent(selected_gem, gem):
			_try_swap(selected_gem, gem)
		else:
			# 不相邻，切换选中
			_deselect_gem()
			_select_gem(gem)


func _handle_swipe(end_pos: Vector2) -> void:
	"""处理滑动：确定方向并尝试交换"""
	if selected_gem == null:
		return

	var delta := end_pos - touch_start_pos
	var direction := Vector2.ZERO

	# 判断主方向（水平 or 垂直）
	if abs(delta.x) > abs(delta.y):
		direction = Vector2(sign(delta.x), 0)
	else:
		direction = Vector2(0, sign(delta.y))

	var target_col := selected_gem.grid_col + int(direction.x)
	var target_row := selected_gem.grid_row + int(direction.y)

	if not GameManager.is_valid_cell(target_col, target_row):
		_deselect_gem()
		return

	var target_gem = grid[target_col][target_row]
	if target_gem != null:
		_try_swap(selected_gem, target_gem)
	else:
		_deselect_gem()


# ── 选中逻辑 ──────────────────────────────────────────────
func _select_gem(gem) -> void:
	selected_gem = gem
	gem.set_selected(true)
	AudioManager.play_sfx("button_click", 0.05)


func _deselect_gem() -> void:
	if selected_gem != null:
		selected_gem.set_selected(false)
		selected_gem = null


# ── 相邻判断 ──────────────────────────────────────────────
func _are_adjacent(gem_a, gem_b) -> bool:
	"""检查两个宝石是否相邻（上下左右）"""
	var dc := abs(gem_a.grid_col - gem_b.grid_col)
	var dr := abs(gem_a.grid_row - gem_b.grid_row)
	return (dc == 1 and dr == 0) or (dc == 0 and dr == 1)


# ── 交换逻辑 ──────────────────────────────────────────────
func _try_swap(gem_a, gem_b) -> void:
	"""尝试交换两个宝石"""
	_deselect_gem()
	is_processing = true
	GameManager.current_state = GameManager.GameState.SWAPPING

	# 播放交换音效
	AudioManager.play_sfx("swap", 0.05)

	# 执行交换动画
	yield(_swap_gems(gem_a, gem_b), "completed")

	# 检查是否形成匹配
	var matches := _find_matches()

	if matches.size() > 0:
		# 有匹配！消耗步数，进入消除循环
		GameManager.use_move()
		yield(_process_matches(matches), "completed")
	else:
		# 无匹配，换回去
		yield(_swap_gems(gem_a, gem_b), "completed")

	is_processing = false
	GameManager.current_state = GameManager.GameState.READY


func _swap_gems(gem_a, gem_b) -> void:
	"""交换两个宝石的位置（动画 + 数据）"""
	# 交换网格数据
	var col_a := gem_a.grid_col
	var row_a := gem_a.grid_row
	var col_b := gem_b.grid_col
	var row_b := gem_b.grid_row

	grid[col_a][row_a] = gem_b
	grid[col_b][row_b] = gem_a

	# 同时播放移动动画
	gem_a.move_to_cell(col_b, row_b)
	gem_b.move_to_cell(col_a, row_a)

	# 等待较长的那个动画完成
	yield(get_tree().create_timer(gem_a.MOVE_DURATION + 0.02), "timeout")


# ── 匹配检测 ──────────────────────────────────────────────
func _find_matches() -> Array:
	"""
	扫描整个棋盘，找出所有三连及以上的匹配。
	返回: Array of Dictionary {gems: [Gem], type: int, is_horizontal: bool}
	"""
	var matches := []

	# 水平扫描
	for row in range(GameManager.BOARD_ROWS):
		var col := 0
		while col < GameManager.BOARD_COLS:
			var gem = grid[col][row]
			if gem == null:
				col += 1
				continue

			var match_gems := [gem]
			var match_col := col + 1
			while match_col < GameManager.BOARD_COLS:
				var next = grid[match_col][row]
				if next != null and next.gem_type == gem.gem_type:
					match_gems.append(next)
					match_col += 1
				else:
					break

			if match_gems.size() >= 3:
				matches.append({
					"gems": match_gems,
					"type": gem.gem_type,
					"is_horizontal": true,
					"length": match_gems.size()
				})
				col = match_col
			else:
				col += 1

	# 垂直扫描
	for col in range(GameManager.BOARD_COLS):
		var row := 0
		while row < GameManager.BOARD_ROWS:
			var gem = grid[col][row]
			if gem == null:
				row += 1
				continue

			var match_gems := [gem]
			var match_row := row + 1
			while match_row < GameManager.BOARD_ROWS:
				var next = grid[col][match_row]
				if next != null and next.gem_type == gem.gem_type:
					match_gems.append(next)
					match_row += 1
				else:
					break

			if match_gems.size() >= 3:
				matches.append({
					"gems": match_gems,
					"type": gem.gem_type,
					"is_horizontal": false,
					"length": match_gems.size()
				})
				row = match_row
			else:
				row += 1

	return matches


# ── 消除处理链 ────────────────────────────────────────────
func _process_matches(matches: Array) -> void:
	"""处理匹配 → 消除 → 下落 → 再检查（循环直到无新匹配）"""
	GameManager.reset_combo()

	while matches.size() > 0:
		GameManager.increment_combo()
		GameManager.current_state = GameManager.GameState.ELIMINATING

		# 计算得分
		var total_gems := 0
		for match_data in matches:
			var length: int = match_data["length"]
			total_gems += length
			# 基础分: 3连=30, 4连=60, 5连=100
			var base_score := 10 * length * (length - 2)
			GameManager.add_score(base_score)

			# 播放匹配音效
			if length >= 5:
				AudioManager.play_sfx("match5", 0.1)
			elif length >= 4:
				AudioManager.play_sfx("match4", 0.1)
			else:
				AudioManager.play_sfx("match3", 0.1)

			# 发送匹配事件（用于目标追踪）
			GameManager.emit_signal("gems_matched", {
				"type": match_data["type"],
				"count": length,
				"is_horizontal": match_data["is_horizontal"]
			})

		# ── 特殊宝石生成逻辑 ─────────────────────────────
		# 4连 → 条纹宝石，5连 → 彩虹宝石
		# T/L形交叉 → 炸弹宝石
		var special_gems_to_create := []  # [{col, row, type}]
		for match_data in matches:
			var length: int = match_data["length"]
			var gems_list: Array = match_data["gems"]
			var is_h: bool = match_data["is_horizontal"]

			if length >= 5:
				# 5连以上 → 彩虹宝石（生成在中间位置）
				var mid = gems_list[length / 2]
				special_gems_to_create.append({
					"col": mid.grid_col,
					"row": mid.grid_row,
					"type": GameManager.GemType.RAINBOW
				})
			elif length == 4:
				# 4连 → 条纹宝石（方向与匹配方向垂直）
				var mid = gems_list[1]  # 第二个位置
				var stripe_type = GameManager.GemType.STRIPED_V if is_h else GameManager.GemType.STRIPED_H
				special_gems_to_create.append({
					"col": mid.grid_col,
					"row": mid.grid_row,
					"type": stripe_type
				})

		# 检测 T/L 形交叉（两个匹配共享一个宝石 → 炸弹）
		for i in range(matches.size()):
			for j in range(i + 1, matches.size()):
				var shared := _find_shared_gem(matches[i]["gems"], matches[j]["gems"])
				if shared != null:
					# 交叉点生成炸弹
					var already_special := false
					for s in special_gems_to_create:
						if s["col"] == shared.grid_col and s["row"] == shared.grid_row:
							already_special = true
							break
					if not already_special:
						special_gems_to_create.append({
							"col": shared.grid_col,
							"row": shared.grid_row,
							"type": GameManager.GemType.BOMB
						})

		# 收集所有需要消除的宝石（去重）
		var gems_to_eliminate := {}
		for match_data in matches:
			for gem in match_data["gems"]:
				var key := "%d_%d" % [gem.grid_col, gem.grid_row]
				gems_to_eliminate[key] = gem

		# 从消除列表中移除将变成特殊宝石的位置
		for special in special_gems_to_create:
			var key := "%d_%d" % [special["col"], special["row"]]
			gems_to_eliminate.erase(key)

		# 检查消除列表中是否有特殊宝石，触发其效果
		var special_keys := []
		for key in gems_to_eliminate:
			var gem = gems_to_eliminate[key]
			if gem.is_special:
				special_keys.append(key)
		for key in special_keys:
			var gem = gems_to_eliminate[key]
			_trigger_special_gem(gem, gems_to_eliminate)

		# 播放消除动画 + 粒子特效
		var delay_step := 0.03
		var i := 0
		var combo := GameManager.combo_count
		for key in gems_to_eliminate:
			var gem = gems_to_eliminate[key]
			var gem_pos := gem.position
			var gem_color: Color = Color.white
			if gem.gem_type >= 0 and gem.gem_type < gem.GEM_COLORS.size():
				gem_color = gem.GEM_COLORS[gem.gem_type]

			grid[gem.grid_col][gem.grid_row] = null
			gem.eliminate(i * delay_step)

			# 生成粒子爆炸效果
			var effect = Node2D.new()
			effect.set_script(MatchEffect)
			effects_container.add_child(effect)
			effect.create_burst(gem_pos, gem_color, combo)

			# 分数飘字（每3个宝石显示一次，避免太密集）
			if i % 3 == 0:
				var popup = Node2D.new()
				popup.set_script(ScorePopup)
				effects_container.add_child(popup)
				var score_per_gem := int(10 * total_gems * GameManager.get_combo_multiplier() / max(total_gems, 1))
				popup.show_score(score_per_gem, gem_pos, gem_color.lightened(0.3))

			i += 1

		# 连击飘字
		if combo >= 2:
			var combo_display = Node2D.new()
			combo_display.set_script(ComboDisplay)
			effects_container.add_child(combo_display)
			var center := GameManager.grid_to_pixel(
				GameManager.BOARD_COLS / 2, GameManager.BOARD_ROWS / 2
			)
			combo_display.show_combo(combo, center + Vector2(0, -100))

		# 创建特殊宝石（在消除动画播放时）
		for special in special_gems_to_create:
			var old_gem = grid[special["col"]][special["row"]]
			if old_gem != null:
				old_gem.queue_free()
			var new_gem := _create_gem(special["type"], special["col"], special["row"])
			new_gem.is_special = true
			new_gem.update()
			# 特殊宝石出场动画
			new_gem.scale = Vector2.ZERO
			var st := Tween.new()
			new_gem.add_child(st)
			st.interpolate_property(
				new_gem, "scale",
				Vector2.ZERO, Vector2.ONE * 1.3,
				0.2, Tween.TRANS_BACK, Tween.EASE_OUT
			)
			st.interpolate_property(
				new_gem, "scale",
				Vector2.ONE * 1.3, Vector2.ONE,
				0.1, Tween.TRANS_QUAD, Tween.EASE_IN,
				0.2
			)
			st.start()
			AudioManager.play_sfx("special_create")

		# 等待消除动画完成
		yield(get_tree().create_timer(
			gems_to_eliminate.size() * delay_step + 0.35
		), "timeout")

		# 下落填充
		GameManager.current_state = GameManager.GameState.FALLING
		yield(_collapse_and_refill(), "completed")

		# 继续检查新的匹配
		matches = _find_matches()

	# 消除链结束
	GameManager.reset_combo()
	GameManager.current_state = GameManager.GameState.MATCHING


# ── 下落 + 填充 ──────────────────────────────────────────
func _collapse_and_refill() -> void:
	"""让悬空的宝石下落，从顶部补充新宝石"""
	var gem_count := LevelManager.get_gem_type_count()
	var max_fall_delay := 0.0

	# 逐列处理下落
	for col in range(GameManager.BOARD_COLS):
		# 从底部向上扫描，找出空位
		var empty_row := GameManager.BOARD_ROWS - 1
		var fall_count := 0

		# 先让已有宝石下落
		for row in range(GameManager.BOARD_ROWS - 1, -1, -1):
			if grid[col][row] != null:
				if row != empty_row:
					# 移动宝石到空位
					var gem = grid[col][row]
					grid[col][row] = null
					grid[col][empty_row] = gem
					var delay := fall_count * 0.04
					gem.fall_to_cell(col, empty_row, delay)
					max_fall_delay = max(max_fall_delay, delay)
					fall_count += 1
				empty_row -= 1
			else:
				fall_count += 1

		# 从上方生成新宝石填充空位
		var new_gem_count := 0
		for row in range(empty_row, -1, -1):
			var gem_type := randi() % gem_count
			var gem := _create_gem(gem_type, col, row)
			# 新宝石从棋盘上方开始下落
			gem.position = GameManager.grid_to_pixel(col, -1 - new_gem_count)
			var delay := (fall_count + new_gem_count) * 0.04
			gem.fall_to_cell(col, row, delay)
			max_fall_delay = max(max_fall_delay, delay)
			new_gem_count += 1

	# 等待所有下落动画完成（FALL_DURATION = 0.22）
	yield(get_tree().create_timer(
		max_fall_delay + 0.32
	), "timeout")


# ── 调试工具 ──────────────────────────────────────────────
func _debug_print_grid() -> void:
	"""打印棋盘状态到控制台（调试用）"""
	var output := "\n[Board] 棋盘状态:\n"
	for row in range(GameManager.BOARD_ROWS):
		var line := ""
		for col in range(GameManager.BOARD_COLS):
			if grid[col][row] != null:
				line += str(grid[col][row].gem_type) + " "
			else:
				line += ". "
		output += line + "\n"
	print(output)


func _on_gem_selected(gem) -> void:
	"""宝石被点击时的处理"""
	if is_processing:
		return
	_handle_touch(gem.position)


# ── 特殊宝石辅助函数 ────────────────────────────────────
func _find_shared_gem(gems_a: Array, gems_b: Array):
	"""查找两个匹配组中共享的宝石（T/L 形检测）"""
	for ga in gems_a:
		for gb in gems_b:
			if ga.grid_col == gb.grid_col and ga.grid_row == gb.grid_row:
				return ga
	return null


func _trigger_special_gem(gem, gems_to_eliminate: Dictionary) -> void:
	"""触发特殊宝石效果，将受影响的宝石加入消除列表"""
	match gem.gem_type:
		GameManager.GemType.STRIPED_H:
			# 横条纹：消除整行
			for col in range(GameManager.BOARD_COLS):
				_add_to_eliminate(col, gem.grid_row, gems_to_eliminate)

		GameManager.GemType.STRIPED_V:
			# 竖条纹：消除整列
			for row in range(GameManager.BOARD_ROWS):
				_add_to_eliminate(gem.grid_col, row, gems_to_eliminate)

		GameManager.GemType.BOMB:
			# 炸弹：3x3 范围
			for dc in range(-1, 2):
				for dr in range(-1, 2):
					var c := gem.grid_col + dc
					var r := gem.grid_row + dr
					_add_to_eliminate(c, r, gems_to_eliminate)

		GameManager.GemType.RAINBOW:
			# 彩虹：消除棋盘上随机一种颜色的所有宝石
			var target_type := _get_most_common_type()
			for col in range(GameManager.BOARD_COLS):
				for row in range(GameManager.BOARD_ROWS):
					if grid[col][row] != null and grid[col][row].gem_type == target_type:
						_add_to_eliminate(col, row, gems_to_eliminate)

	AudioManager.play_sfx("special_explode")


func _add_to_eliminate(col: int, row: int, gems_dict: Dictionary) -> void:
	"""安全地将一个位置的宝石加入消除列表"""
	if not GameManager.is_valid_cell(col, row):
		return
	if grid[col][row] == null:
		return
	var key := "%d_%d" % [col, row]
	gems_dict[key] = grid[col][row]


func _get_most_common_type() -> int:
	"""找出棋盘上数量最多的普通宝石类型"""
	var counts := {}
	for col in range(GameManager.BOARD_COLS):
		for row in range(GameManager.BOARD_ROWS):
			if grid[col][row] != null:
				var t = grid[col][row].gem_type
				if t < GameManager.NORMAL_GEM_COUNT:
					counts[t] = counts.get(t, 0) + 1
	var best_type := 0
	var best_count := 0
	for t in counts:
		if counts[t] > best_count:
			best_count = counts[t]
			best_type = t
	return best_type

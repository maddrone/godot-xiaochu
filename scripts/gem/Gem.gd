extends Node2D
# ============================================================
# Gem.gd — 单个宝石节点
# 职责：显示、动画、状态管理
# 使用 _draw() 程序化绘制宝石（无需外部贴图，低端友好）
# 场景结构：
#   Gem (Node2D)
#     ├── Area2D + CollisionShape2D  — 点击检测
#     └── Tween                       — 动画过渡
# ============================================================

signal gem_selected(gem)        # 宝石被点击选中
signal move_completed(gem)      # 移动动画完成
signal eliminate_completed(gem)  # 消除动画完成

# ── 宝石属性 ──────────────────────────────────────────────
var gem_type: int = -1         # GemType 枚举值
var grid_col: int = 0          # 网格列
var grid_row: int = 0          # 网格行
var is_selected: bool = false  # 是否被选中
var is_matched: bool = false   # 是否被标记为匹配（待消除）
var is_moving: bool = false    # 是否在移动中
var is_special: bool = false   # 是否为特殊宝石

# ── 宝石颜色配置 ─────────────────────────────────────────
const GEM_COLORS := [
	Color("e84855"),    # RED - 红
	Color("f79824"),    # ORANGE - 橙
	Color("f9dc5c"),    # YELLOW - 黄
	Color("3bb273"),    # GREEN - 绿
	Color("4895ef"),    # BLUE - 蓝
	Color("9b5de5"),    # PURPLE - 紫
]

# 宝石形状（用不同多边形区分颜色，增加辨识度）
# 0=圆, 1=菱形, 2=三角, 3=方形, 4=五角, 5=六角
const GEM_SHAPES := [0, 1, 2, 3, 4, 5]

# ── 绘制参数 ──────────────────────────────────────────────
const GEM_RADIUS := 28.0        # 宝石半径
const GEM_INNER_RADIUS := 20.0  # 内部高光半径
const OUTLINE_WIDTH := 2.0      # 轮廓线宽

# ── 动画参数 ──────────────────────────────────────────────
const MOVE_DURATION := 0.18     # 移动动画时长（快速=爽）
const FALL_DURATION := 0.22     # 下落动画时长
const ELIMINATE_DURATION := 0.25 # 消除动画时长
const SELECT_SCALE := 1.15      # 选中放大倍率
const BOUNCE_SCALE := 1.2       # 弹跳放大

# ── 子节点引用 ────────────────────────────────────────────
onready var tween: Tween = $Tween


func _ready() -> void:
	update()  # 触发 _draw


# ── 初始化 ────────────────────────────────────────────────
func init(type: int, col: int, row: int) -> void:
	"""初始化宝石类型和位置"""
	gem_type = type
	grid_col = col
	grid_row = row
	is_special = type >= GameManager.NORMAL_GEM_COUNT
	position = GameManager.grid_to_pixel(col, row)
	update()


# ── 程序化绘制 ────────────────────────────────────────────
func _draw() -> void:
	"""用不同形状+颜色绘制宝石"""
	if gem_type < 0:
		return

	var color: Color
	var shape_id: int

	if gem_type < GEM_COLORS.size():
		color = GEM_COLORS[gem_type]
		shape_id = GEM_SHAPES[gem_type]
	else:
		# 特殊宝石
		color = Color.white
		shape_id = 0

	# 绘制阴影（偏移2像素）
	var shadow_color := Color(0, 0, 0, 0.3)
	_draw_gem_shape(Vector2(2, 2), GEM_RADIUS, shadow_color, shape_id)

	# 绘制主体
	_draw_gem_shape(Vector2.ZERO, GEM_RADIUS, color, shape_id)

	# 绘制高光（内部更亮的小形状）
	var highlight := color.lightened(0.35)
	_draw_gem_shape(Vector2(-3, -3), GEM_INNER_RADIUS * 0.6, highlight, shape_id)

	# 选中指示器
	if is_selected:
		_draw_gem_shape(Vector2.ZERO, GEM_RADIUS + 4, Color(1, 1, 1, 0.4), 0)


func _draw_gem_shape(offset: Vector2, radius: float, color: Color, shape_id: int) -> void:
	"""根据 shape_id 绘制不同形状"""
	match shape_id:
		0:  # 圆形
			draw_circle(offset, radius, color)
		1:  # 菱形
			var points := PoolVector2Array([
				offset + Vector2(0, -radius),
				offset + Vector2(radius, 0),
				offset + Vector2(0, radius),
				offset + Vector2(-radius, 0)
			])
			draw_colored_polygon(points, color)
		2:  # 三角形
			var points := PoolVector2Array([
				offset + Vector2(0, -radius),
				offset + Vector2(radius * 0.87, radius * 0.5),
				offset + Vector2(-radius * 0.87, radius * 0.5)
			])
			draw_colored_polygon(points, color)
		3:  # 圆角方形（用多边形近似）
			var r := radius * 0.85
			var points := PoolVector2Array([
				offset + Vector2(-r, -r),
				offset + Vector2(r, -r),
				offset + Vector2(r, r),
				offset + Vector2(-r, r)
			])
			draw_colored_polygon(points, color)
		4:  # 五角星
			_draw_star(offset, radius, 5, color)
		5:  # 六边形
			_draw_polygon(offset, radius, 6, color)


func _draw_star(center: Vector2, radius: float, points: int, color: Color) -> void:
	"""绘制星形"""
	var inner := radius * 0.5
	var verts := PoolVector2Array()
	for i in range(points * 2):
		var angle := (PI * 2.0 * i / (points * 2)) - PI / 2
		var r := radius if i % 2 == 0 else inner
		verts.append(center + Vector2(cos(angle), sin(angle)) * r)
	draw_colored_polygon(verts, color)


func _draw_polygon(center: Vector2, radius: float, sides: int, color: Color) -> void:
	"""绘制正多边形"""
	var verts := PoolVector2Array()
	for i in range(sides):
		var angle := (PI * 2.0 * i / sides) - PI / 2
		verts.append(center + Vector2(cos(angle), sin(angle)) * radius)
	draw_colored_polygon(verts, color)


# ── 选中状态 ──────────────────────────────────────────────
func set_selected(selected: bool) -> void:
	is_selected = selected
	update()  # 重绘选中指示
	if selected:
		tween.stop_all()
		tween.interpolate_property(
			self, "scale",
			scale, Vector2.ONE * SELECT_SCALE,
			0.15, Tween.TRANS_BACK, Tween.EASE_OUT
		)
		tween.start()
	else:
		tween.stop_all()
		tween.interpolate_property(
			self, "scale",
			scale, Vector2.ONE,
			0.1, Tween.TRANS_QUAD, Tween.EASE_OUT
		)
		tween.start()


# ── 移动动画 ──────────────────────────────────────────────
func move_to_cell(col: int, row: int, duration := MOVE_DURATION) -> void:
	"""平滑移动到新的网格位置"""
	grid_col = col
	grid_row = row
	is_moving = true
	var target_pos := GameManager.grid_to_pixel(col, row)
	tween.stop_all()
	tween.interpolate_property(
		self, "position",
		position, target_pos,
		duration, Tween.TRANS_QUAD, Tween.EASE_IN_OUT
	)
	tween.start()
	yield(tween, "tween_all_completed")
	is_moving = false
	emit_signal("move_completed", self)


func fall_to_cell(col: int, row: int, delay: float = 0.0) -> void:
	"""下落到新位置（带延迟和弹跳感）"""
	grid_col = col
	grid_row = row
	is_moving = true
	var target_pos := GameManager.grid_to_pixel(col, row)

	if delay > 0.0:
		yield(get_tree().create_timer(delay), "timeout")

	tween.stop_all()
	tween.interpolate_property(
		self, "position",
		position, target_pos,
		FALL_DURATION, Tween.TRANS_BOUNCE, Tween.EASE_OUT
	)
	tween.start()
	yield(tween, "tween_all_completed")
	is_moving = false
	emit_signal("move_completed", self)


# ── 消除动画 ──────────────────────────────────────────────
func eliminate(delay: float = 0.0) -> void:
	"""播放消除动画并销毁自己"""
	is_matched = true

	if delay > 0.0:
		yield(get_tree().create_timer(delay), "timeout")

	tween.stop_all()
	# 先放大再缩小消失（"砰"的感觉）
	tween.interpolate_property(
		self, "scale",
		Vector2.ONE, Vector2.ONE * BOUNCE_SCALE,
		ELIMINATE_DURATION * 0.3,
		Tween.TRANS_QUAD, Tween.EASE_OUT
	)
	tween.interpolate_property(
		self, "scale",
		Vector2.ONE * BOUNCE_SCALE, Vector2.ZERO,
		ELIMINATE_DURATION * 0.7,
		Tween.TRANS_BACK, Tween.EASE_IN,
		ELIMINATE_DURATION * 0.3
	)
	tween.interpolate_property(
		self, "modulate:a",
		1.0, 0.0,
		ELIMINATE_DURATION,
		Tween.TRANS_QUAD, Tween.EASE_IN
	)
	tween.start()
	yield(tween, "tween_all_completed")
	emit_signal("eliminate_completed", self)
	queue_free()


# ── 出场动画（从上方掉入） ──────────────────────────────
func spawn_animation(delay: float = 0.0) -> void:
	"""出场动画：从棋盘上方掉入"""
	var target_pos := position
	position.y -= GameManager.CELL_SIZE * 2
	scale = Vector2.ONE * 0.5
	modulate.a = 0.0

	if delay > 0.0:
		yield(get_tree().create_timer(delay), "timeout")

	tween.stop_all()
	tween.interpolate_property(
		self, "position",
		position, target_pos,
		FALL_DURATION * 1.2,
		Tween.TRANS_BOUNCE, Tween.EASE_OUT
	)
	tween.interpolate_property(
		self, "scale",
		Vector2.ONE * 0.5, Vector2.ONE,
		FALL_DURATION,
		Tween.TRANS_BACK, Tween.EASE_OUT
	)
	tween.interpolate_property(
		self, "modulate:a",
		0.0, 1.0,
		FALL_DURATION * 0.5,
		Tween.TRANS_LINEAR, Tween.EASE_IN
	)
	tween.start()


# ── 输入处理 ──────────────────────────────────────────────
func _on_input_event(_viewport, event, _shape_idx) -> void:
	"""Area2D 的输入事件"""
	if not GameManager.is_input_allowed():
		return
	if event is InputEventMouseButton and event.pressed:
		emit_signal("gem_selected", self)

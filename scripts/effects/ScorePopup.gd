extends Node2D
# ============================================================
# ScorePopup.gd — 分数飘字
# 职责：在消除位置显示获得的分数
# ============================================================


func show_score(points: int, pos: Vector2, color: Color = Color.white) -> void:
	"""显示分数飘字"""
	position = pos
	scale = Vector2.ZERO
	modulate.a = 1.0

	var tween := Tween.new()
	add_child(tween)

	# 弹出
	tween.interpolate_property(
		self, "scale",
		Vector2.ZERO, Vector2.ONE,
		0.15, Tween.TRANS_BACK, Tween.EASE_OUT
	)
	# 上飘
	tween.interpolate_property(
		self, "position:y",
		position.y, position.y - 40,
		0.6, Tween.TRANS_QUAD, Tween.EASE_OUT
	)
	# 淡出
	tween.interpolate_property(
		self, "modulate:a",
		1.0, 0.0,
		0.3, Tween.TRANS_QUAD, Tween.EASE_IN,
		0.4
	)
	tween.start()

	# 存储绘制数据
	set_meta("points", points)
	set_meta("color", color)
	update()

	yield(get_tree().create_timer(0.8), "timeout")
	queue_free()


func _draw() -> void:
	if not has_meta("points"):
		return
	var points: int = get_meta("points")
	var color: Color = get_meta("color")
	var text := "+%d" % points

	var font := Control.new().get_font("font")
	if font == null:
		return
	var text_size := font.get_string_size(text)
	var offset := -text_size / 2

	# 描边
	for dir in [Vector2(-1, 0), Vector2(1, 0), Vector2(0, -1), Vector2(0, 1)]:
		draw_string(font, offset + dir, text, Color(0, 0, 0, 0.6))
	draw_string(font, offset, text, color)

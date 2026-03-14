extends Node2D
# ============================================================
# ComboDisplay.gd — 连击数字飘字效果
# 职责：在屏幕上显示 "COMBO x3!" 等连击文字
# 使用 Tween 实现弹出+上飘+淡出动画
# ============================================================

var combo_count := 0
var display_text := ""


func show_combo(combo: int, pos: Vector2) -> void:
	"""显示连击飘字"""
	combo_count = combo
	position = pos

	# 构造显示文本
	if combo >= 6:
		display_text = "INCREDIBLE! x%d" % combo
	elif combo >= 4:
		display_text = "AMAZING! x%d" % combo
	elif combo >= 3:
		display_text = "GREAT! x%d" % combo
	elif combo >= 2:
		display_text = "COMBO x%d" % combo
	else:
		queue_free()
		return

	# 初始状态
	scale = Vector2.ZERO
	modulate.a = 1.0
	update()

	# 弹出动画
	var tween := Tween.new()
	add_child(tween)

	# 弹性放大
	tween.interpolate_property(
		self, "scale",
		Vector2.ZERO, Vector2.ONE * (1.0 + combo * 0.1),
		0.2, Tween.TRANS_BACK, Tween.EASE_OUT
	)
	# 上飘
	tween.interpolate_property(
		self, "position:y",
		position.y, position.y - 60,
		0.8, Tween.TRANS_QUAD, Tween.EASE_OUT
	)
	# 延迟淡出
	tween.interpolate_property(
		self, "modulate:a",
		1.0, 0.0,
		0.4, Tween.TRANS_QUAD, Tween.EASE_IN,
		0.5  # 0.5 秒后开始淡出
	)
	tween.start()

	yield(get_tree().create_timer(1.0), "timeout")
	queue_free()


func _draw() -> void:
	if display_text == "":
		return

	# 文字颜色随连击变化
	var text_color := Color.white
	if combo_count >= 6:
		text_color = Color("ff4444")
	elif combo_count >= 4:
		text_color = Color("ff8844")
	elif combo_count >= 3:
		text_color = Color("ffcc00")

	# 绘制阴影
	var font := Control.new().get_font("font")
	if font == null:
		return
	var text_size := font.get_string_size(display_text)
	var offset := -text_size / 2

	# 描边（四方向偏移模拟）
	var outline_color := Color(0, 0, 0, 0.7)
	for dir in [Vector2(-2, 0), Vector2(2, 0), Vector2(0, -2), Vector2(0, 2)]:
		draw_string(font, offset + dir, display_text, outline_color)

	# 主文字
	draw_string(font, offset, display_text, text_color)

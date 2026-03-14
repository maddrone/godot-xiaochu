extends CanvasLayer
# ============================================================
# GameUI.gd — 游戏内 HUD
# 职责：显示分数、步数、连击、关卡目标
# 使用 CanvasLayer 确保 UI 不受 Camera2D 影响
# ============================================================

onready var score_label: Label = $TopBar/ScoreLabel
onready var moves_label: Label = $TopBar/MovesLabel
onready var level_label: Label = $TopBar/LevelLabel
onready var combo_label: Label = $ComboLabel
onready var combo_tween: Tween = $ComboTween
onready var objective_container: VBoxContainer = $ObjectivePanel/VBox


func _ready() -> void:
	# 连接信号
	GameManager.connect("score_changed", self, "_on_score_changed")
	GameManager.connect("moves_changed", self, "_on_moves_changed")
	GameManager.connect("combo_changed", self, "_on_combo_changed")
	GameManager.connect("combo_ended", self, "_on_combo_ended")
	GameManager.connect("level_completed", self, "_on_level_completed")
	GameManager.connect("level_failed", self, "_on_level_failed")
	LevelManager.connect("level_loaded", self, "_on_level_loaded")
	LevelManager.connect("objective_updated", self, "_on_objective_updated")

	combo_label.visible = false
	_update_score(0)
	_update_moves(30)


# ── 信号回调 ──────────────────────────────────────────────
func _on_score_changed(new_score: int) -> void:
	_update_score(new_score)


func _on_moves_changed(remaining: int) -> void:
	_update_moves(remaining)


func _on_combo_changed(combo: int) -> void:
	if combo >= 2:
		_show_combo(combo)


func _on_combo_ended() -> void:
	_hide_combo()


func _on_level_loaded(level_data: Dictionary) -> void:
	level_label.text = "Level %d" % level_data.get("id", 1)
	_update_moves(level_data.get("moves", 30))
	_setup_objectives(level_data)


func _on_level_completed() -> void:
	_show_result_popup("LEVEL CLEAR!", Color("3bb273"))


func _on_level_failed() -> void:
	_show_result_popup("GAME OVER", Color("e84855"))


func _on_objective_updated(obj_id: int, current: int, target: int) -> void:
	# 更新目标显示
	if obj_id < objective_container.get_child_count():
		var label: Label = objective_container.get_child(obj_id)
		label.text = label.get_meta("base_text") + " %d/%d" % [min(current, target), target]
		if current >= target:
			label.modulate = Color("3bb273")  # 完成变绿


# ── UI 更新 ──────────────────────────────────────────────
func _update_score(value: int) -> void:
	score_label.text = "SCORE: %d" % value


func _update_moves(value: int) -> void:
	moves_label.text = "MOVES: %d" % value
	if value <= 5:
		moves_label.modulate = Color("e84855")  # 步数不足变红
	else:
		moves_label.modulate = Color.white


func _show_combo(combo: int) -> void:
	combo_label.visible = true
	combo_label.text = "COMBO x%d!" % combo

	# 颜色随连击变化
	if combo >= 6:
		combo_label.modulate = Color("ff4444")
	elif combo >= 4:
		combo_label.modulate = Color("ff8844")
	elif combo >= 3:
		combo_label.modulate = Color("ffcc00")
	else:
		combo_label.modulate = Color.white

	# 弹性缩放动画
	combo_tween.stop_all()
	combo_tween.interpolate_property(
		combo_label, "rect_scale",
		Vector2.ONE * 1.5, Vector2.ONE,
		0.2, Tween.TRANS_BACK, Tween.EASE_OUT
	)
	combo_tween.start()


func _hide_combo() -> void:
	combo_tween.stop_all()
	combo_tween.interpolate_property(
		combo_label, "modulate:a",
		1.0, 0.0,
		0.3, Tween.TRANS_QUAD, Tween.EASE_IN
	)
	combo_tween.start()
	yield(combo_tween, "tween_all_completed")
	combo_label.visible = false
	combo_label.modulate.a = 1.0


func _setup_objectives(level_data: Dictionary) -> void:
	"""根据关卡数据设置目标显示"""
	# 清除旧目标
	for child in objective_container.get_children():
		child.queue_free()

	var objectives = level_data.get("objectives", [])
	for obj in objectives:
		var label := Label.new()
		var obj_text := ""
		match obj.get("type", ""):
			"SCORE":
				obj_text = "Score: "
			"COLLECT_GEM":
				obj_text = "Collect %s: " % obj.get("gem_type", "?")
			"COMBO_REACH":
				obj_text = "Combo: "
			"CLEAR_BLOCKER":
				obj_text = "Clear: "

		label.set_meta("base_text", obj_text)
		label.text = obj_text + "0/%d" % obj.get("target", 0)
		label.align = Label.ALIGN_CENTER
		label.add_color_override("font_color", Color.white)
		objective_container.add_child(label)


func _show_result_popup(text: String, color: Color) -> void:
	"""显示结果弹窗（通关/失败）"""
	var popup := Label.new()
	popup.text = text
	popup.align = Label.ALIGN_CENTER
	popup.valign = Label.VALIGN_CENTER
	popup.rect_min_size = Vector2(400, 100)
	popup.rect_position = Vector2(160, 500)
	popup.add_color_override("font_color", color)
	add_child(popup)

	# 弹出动画
	popup.rect_scale = Vector2.ZERO
	var t := Tween.new()
	add_child(t)
	t.interpolate_property(
		popup, "rect_scale",
		Vector2.ZERO, Vector2.ONE * 2.0,
		0.4, Tween.TRANS_BACK, Tween.EASE_OUT
	)
	t.start()

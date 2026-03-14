extends Camera2D
# ============================================================
# ScreenShake.gd — 屏幕抖动效果
# 挂载到 Camera2D 上，通过 GameManager 信号触发
# ============================================================

var shake_intensity := 0.0
var shake_decay := 5.0  # 衰减速度


func _ready() -> void:
	GameManager.connect("combo_changed", self, "_on_combo_changed")
	GameManager.connect("gems_matched", self, "_on_gems_matched")


func _process(delta: float) -> void:
	if shake_intensity > 0.1:
		offset = Vector2(
			rand_range(-shake_intensity, shake_intensity),
			rand_range(-shake_intensity, shake_intensity)
		)
		shake_intensity = lerp(shake_intensity, 0.0, shake_decay * delta)
	else:
		shake_intensity = 0.0
		offset = Vector2.ZERO


func shake(intensity: float) -> void:
	"""触发抖动"""
	shake_intensity = max(shake_intensity, intensity)


func _on_combo_changed(combo: int) -> void:
	if combo >= 2:
		shake(GameManager.get_screen_shake_intensity())


func _on_gems_matched(match_data: Dictionary) -> void:
	var length: int = match_data.get("count", 3)
	if length >= 4:
		shake(3.0 + (length - 4) * 2.0)

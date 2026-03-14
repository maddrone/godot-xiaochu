extends Node2D
# ============================================================
# MatchEffect.gd — 消除粒子特效
# 职责：在宝石消除时产生粒子爆炸效果
# 使用 CPUParticles2D（GLES2 兼容，低端友好）
# 粒子数量和强度随连击缩放，但有上限控制
# ============================================================

# ── 配置 ──────────────────────────────────────────────────
const BASE_PARTICLE_COUNT := 8    # 基础粒子数
const MAX_PARTICLE_COUNT := 24    # 最大粒子数（低端保护）
const EFFECT_LIFETIME := 0.6      # 特效存活时间
const SPARKLE_COUNT := 4          # 闪光粒子数


func create_burst(pos: Vector2, color: Color, combo: int = 1) -> void:
	"""在指定位置创建一次爆炸粒子效果"""
	position = pos

	# 根据连击计算粒子强度
	var intensity := GameManager.get_particle_intensity()
	var particle_count := int(clamp(
		BASE_PARTICLE_COUNT * intensity,
		BASE_PARTICLE_COUNT,
		MAX_PARTICLE_COUNT
	))

	# 主爆炸粒子
	var particles := CPUParticles2D.new()
	particles.emitting = true
	particles.one_shot = true
	particles.explosiveness = 0.9
	particles.amount = particle_count
	particles.lifetime = EFFECT_LIFETIME

	# 粒子参数
	particles.direction = Vector2.ZERO
	particles.spread = 180.0
	particles.initial_velocity = 120.0 * intensity
	particles.initial_velocity_random = 0.4
	particles.gravity = Vector2(0, 200)
	particles.scale_amount = 6.0
	particles.scale_amount_random = 0.3

	# 颜色渐变：从亮色到透明
	var gradient := Gradient.new()
	gradient.set_color(0, color.lightened(0.3))
	gradient.set_color(1, Color(color.r, color.g, color.b, 0.0))
	particles.color_ramp = gradient

	# 初始颜色
	particles.color = color

	add_child(particles)

	# 闪光粒子（更大、更少、更快消失）
	if combo >= 2:
		var sparkles := CPUParticles2D.new()
		sparkles.emitting = true
		sparkles.one_shot = true
		sparkles.explosiveness = 1.0
		sparkles.amount = SPARKLE_COUNT
		sparkles.lifetime = EFFECT_LIFETIME * 0.5

		sparkles.direction = Vector2.ZERO
		sparkles.spread = 180.0
		sparkles.initial_velocity = 200.0 * intensity
		sparkles.initial_velocity_random = 0.3
		sparkles.gravity = Vector2(0, 100)
		sparkles.scale_amount = 10.0

		var spark_gradient := Gradient.new()
		spark_gradient.set_color(0, Color.white)
		spark_gradient.set_color(1, Color(1, 1, 1, 0))
		sparkles.color_ramp = spark_gradient
		sparkles.color = Color.white

		add_child(sparkles)

	# 高连击时添加环形冲击波效果（用 _draw 模拟）
	if combo >= 4:
		_create_shockwave(color, intensity)

	# 自动清理
	yield(get_tree().create_timer(EFFECT_LIFETIME + 0.2), "timeout")
	queue_free()


func _create_shockwave(color: Color, intensity: float) -> void:
	"""创建冲击波环（Tween 驱动的圆环扩展）"""
	var ring := ShockwaveRing.new()
	ring.ring_color = Color(color.r, color.g, color.b, 0.6)
	ring.max_radius = 50.0 * intensity
	add_child(ring)
	ring.start()


# ── 内部类：冲击波环 ────────────────────────────────────
class ShockwaveRing extends Node2D:
	var ring_color := Color.white
	var max_radius := 50.0
	var current_radius := 0.0
	var current_alpha := 0.8
	var ring_width := 3.0

	func start() -> void:
		var tween := Tween.new()
		add_child(tween)
		tween.interpolate_property(
			self, "current_radius",
			0.0, max_radius,
			0.3, Tween.TRANS_QUAD, Tween.EASE_OUT
		)
		tween.interpolate_property(
			self, "current_alpha",
			0.8, 0.0,
			0.3, Tween.TRANS_QUAD, Tween.EASE_IN
		)
		tween.start()
		tween.connect("tween_all_completed", self, "_on_done")

	func _process(_delta: float) -> void:
		update()

	func _draw() -> void:
		if current_radius > 0:
			draw_arc(
				Vector2.ZERO, current_radius,
				0, TAU, 32,
				Color(ring_color.r, ring_color.g, ring_color.b, current_alpha),
				ring_width, true
			)

	func _on_done() -> void:
		queue_free()

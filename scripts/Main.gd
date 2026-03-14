extends Node2D
# ============================================================
# Main.gd — 主场景入口
# 职责：初始化游戏、加载关卡、管理场景切换
# ============================================================

var board_scene := preload("res://scenes/board/Board.tscn")
var board_instance = null


func _ready() -> void:
	print("=== 开心消消乐 ===")
	print("[Main] 游戏启动")
	_start_game()


func _start_game() -> void:
	"""启动游戏流程"""
	var level_data = LevelManager.load_level(1)
	print("[Main] 关卡: %s" % str(level_data.get("name", "未知")))

	board_instance = board_scene.instance()
	add_child(board_instance)

	GameManager.current_state = GameManager.GameState.READY
	print("[Main] 游戏状态: READY")


func _unhandled_input(event: InputEvent) -> void:
	# 开发调试：按 ESC 退出
	if event is InputEventKey and event.pressed:
		match event.scancode:
			KEY_ESCAPE:
				get_tree().quit()
			KEY_R:
				# 重新开始当前关卡
				get_tree().reload_current_scene()

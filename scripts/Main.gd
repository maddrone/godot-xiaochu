extends Node2D
# ============================================================
# Main.gd — 主场景入口
# 职责：初始化游戏、加载关卡、管理场景切换
# ============================================================

# 棋盘场景（模块2 中创建）
var board_scene := preload("res://scenes/board/Board.tscn") if ResourceLoader.exists("res://scenes/board/Board.tscn") else null
var board_instance = null


func _ready() -> void:
	print("=== 开心消消乐 ===")
	print("[Main] 游戏启动")
	print("[Main] 渲染器: %s" % OS.get_current_video_driver())
	print("[Main] 分辨率: %s" % str(OS.window_size))

	# 启动第一关
	_start_game()


func _start_game() -> void:
	"""启动游戏流程"""
	# 加载第一关数据
	var level_data = LevelManager.load_level(1)
	print("[Main] 关卡数据: %s" % str(level_data.get("name", "未知")))

	# 实例化棋盘（如果场景存在）
	if board_scene:
		board_instance = board_scene.instance()
		add_child(board_instance)
		print("[Main] 棋盘已加载")
	else:
		print("[Main] 棋盘场景尚未创建（模块2）")

	# 设置游戏状态为就绪
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

package main

func login(user string) bool {
	// 检查用户名
	if user == "admin" || user == "root" || user == "guest" {
		return true
	}
	return false
}

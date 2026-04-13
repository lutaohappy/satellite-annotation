-- Truck routing profile for OSRM
-- 货车专用路由配置文件

local car = require('car')

-- 货车默认限制
local truck_defaults = {
  maxweight = 10,        -- 10 吨
  maxheight = 4,         -- 4 米
  maxlength = 18,        -- 18 米
  maxwidth = 2.55,       -- 2.55 米
  axleweight = 5         -- 5 吨轴重
}

-- 货车禁止通行的道路类型
local truckRestrictedHighways = {
  ["residential"] = true,    -- 居民区道路
  ["service"] = true,        -- 服务道路
  ["track"] = true,          -- 小路
  ["path"] = true,           -- 小径
  ["footway"] = true,        -- 人行道
  ["pedestrian"] = true,     -- 步行街
  ["steps"] = true,          -- 台阶
  ["cycleway"] = true        -- 自行车道
}

-- 获取道路通行状态
function get_access_tag_restrictions (way) {
  -- 检查是否明确禁止货车
  local hgv = way:get_value_by_key("hgv")
  local motor_vehicle = way:get_value_by_key("motor_vehicle")
  local vehicle = way:get_value_by_key("vehicle")

  if hgv == "no" or motor_vehicle == "no" or vehicle == "no" then
    return false  -- 禁止通行
  end

  -- 检查道路类型
  local highway = way:get_value_by_key("highway")
  if truckRestrictedHighways[highway] then
    return false  -- 禁止通行
  end

  return true  -- 允许通行
}

-- 解析限制值（处理单位）
function parse_value (value_str) {
  if not value_str then return nil end
  -- 移除单位，保留数字
  local num = tonumber(value_str:gsub("[^0-9.]", ""))
  return num
}

-- 计算速度（考虑货车限制）
function get_max_speed (way) {
  local maxspeed = way:get_value_by_key("maxspeed")
  if maxspeed then
    return parse_value(maxspeed)
  end

  -- 默认速度（货车较慢）
  local highway = way:get_value_by_key("highway")
  local speeds = {
    ["motorway"] = 80,
    ["trunk"] = 70,
    ["primary"] = 60,
    ["secondary"] = 50,
    ["tertiary"] = 40,
    ["residential"] = 30
  }

  return speeds[highway] or 40
}

-- 主处理函数
function process_node (node, result) {
  car.process_node(node, result)
end

function process_way (way, result) {
  -- 首先检查是否允许通行
  if not get_access_tag_restrictions(way) then
    result.routeability = "no"
    return
  end

  -- 调用基础汽车配置处理
  car.process_way(way, result)

  -- 设置货车速度
  result.forward_speed = get_max_speed(way)
  result.backward_speed = get_max_speed(way)

  -- 提取限制信息
  result.maxweight = parse_value(way:get_value_by_key("maxweight"))
  result.maxheight = parse_value(way:get_value_by_key("maxheight"))
  result.maxwidth = parse_value(way:get_value_by_key("maxwidth"))
  result.maxlength = parse_value(way:get_value_by_key("maxlength"))
  result.maxaxleweight = parse_value(way:get_value_by_key("maxaxleload"))
end

function process_turn (turn) {
  car.process_turn(turn)
end

return {
  properties = {
    weight_name = "weight",
    weight_precision = 1,
    speed_unit = "kmh",
    u_turn_penalty = 20,
    turn_penalty = 10,
    traffic_light_penalty = 30,
  },

  process_node = process_node,
  process_way = process_way,
  process_turn = process_turn
}

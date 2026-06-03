#include <WiFi.h>
#include <PubSubClient.h>
#include <DHT.h>

/* ================= WiFi 配置 ================= */
const char* ssid = "HUAPPLE 17 Pro Max 1TB";   // ← 改成你的WiFi名
const char* password = "51522zzwlwlbb";          // ← 改成你的WiFi密码

/* ================= MQTT 配置 ================= */
const char* mqtt_server = "broker.emqx.io";
const int mqtt_port = 1883;

const char* TOPIC_CONTROL = "agri/device001/control";   // 订阅: 接收控制指令
const char* TOPIC_DATA    = "agri/device001/data";      // 发布: 传感器数据
const char* TOPIC_STATUS  = "agri/device001/status";    // 发布: 设备状态

/* ================= 传感器引脚 (ESP32) ================= */
// DHT11 温湿度传感器
#define DHTPIN  5       // GPIO5 (对应ESP32开发板的D5)
#define DHTTYPE DHT11

// 水位传感器 (模拟输入)
#define WATER_PIN 34    // GPIO34 (ESP32 ADC1, 仅输入, 无上拉下拉)

/* ================= 阈值配置 ================= */
// ESP32 ADC 为12位 (0-4095), ESP8266为10位 (0-1023)
// 根据实际传感器调整阈值
float TEMP_THRESHOLD = 30.0;
int   WATER_THRESHOLD = 1200;  // 水位阈值 (已调整为ESP32的12位ADC范围)

/* ================= 全局对象 ================= */
WiFiClient espClient;
PubSubClient client(espClient);
DHT dht(DHTPIN, DHTTYPE);

/* ================= 状态变量 ================= */
bool isSending = true;                          // 默认启动后自动开始发送
unsigned long lastSendTime = 0;
const unsigned long SEND_INTERVAL = 5000;       // 发送间隔5秒
unsigned long sendCount = 0;

float temperature = 0.0;
float humidity = 0.0;
int waterValue = 0;

bool sensorError = false;
String sensorErrorMessage = "";

bool linkageAlarm = false;                      // 温度 + 水位联动告警

/* ================= WiFi 连接 ================= */
void setupWiFi() {
  Serial.print("连接 WiFi: ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
    if (++attempts > 40) {
      Serial.println("\nWiFi 连接超时, 重启...");
      ESP.restart();
    }
  }

  Serial.println("\nWiFi 连接成功");
  Serial.print("IP: ");
  Serial.println(WiFi.localIP());
}

/* ================= MQTT 回调 (接收控制指令) ================= */
void callback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (unsigned int i = 0; i < length; i++) {
    message += (char)payload[i];
  }

  Serial.printf("MQTT [%s]: %s\n", topic, message.c_str());

  if (String(topic) == TOPIC_CONTROL) {
    if (message.equalsIgnoreCase("start")) {
      isSending = true;
      lastSendTime = millis();
      sendCount = 0;
      client.publish(TOPIC_STATUS, "{\"status\":\"started\"}");
      Serial.println("收到指令: 开始发送");
    }
    else if (message.equalsIgnoreCase("stop")) {
      isSending = false;
      client.publish(TOPIC_STATUS, "{\"status\":\"stopped\"}");
      Serial.println("收到指令: 停止发送");
    }
    else if (message.equalsIgnoreCase("read")) {
      readSensorData();
      sendSensorData();
      Serial.println("收到指令: 读取一次");
    }
    else if (message.equalsIgnoreCase("status")) {
      sendStatusUpdate();
      Serial.println("收到指令: 发送状态");
    }
  }
}

/* ================= MQTT 重连 ================= */
void reconnect() {
  while (!client.connected()) {
    String clientId = "ESP32-" + String(random(0xffff), HEX);
    if (client.connect(clientId.c_str())) {
      client.subscribe(TOPIC_CONTROL);

      String onlineMsg = "{\"deviceId\":\"ESP32_001\",\"status\":\"online\",\"ip\":\"";
      onlineMsg += WiFi.localIP().toString();
      onlineMsg += "\"}";
      client.publish(TOPIC_STATUS, onlineMsg.c_str());
      Serial.println("MQTT 已连接");
    } else {
      Serial.print(".");
      delay(5000);
    }
  }
}

/* ================= 读取水位传感器 ================= */
void readWaterSensor() {
  // ESP32 ADC: 12位, 0-4095
  // 取多次平均值以减少噪声
  long sum = 0;
  for (int i = 0; i < 10; i++) {
    sum += analogRead(WATER_PIN);
    delay(5);
  }
  waterValue = sum / 10;
}

/* ================= 读取温湿度传感器 ================= */
void readSensorData() {
  static unsigned long lastReadTime = 0;
  if (millis() - lastReadTime < 2000) return;  // DHT11 最少2秒读一次

  float h = dht.readHumidity();
  float t = dht.readTemperature();

  if (isnan(h) || isnan(t)) {
    sensorError = true;
    sensorErrorMessage = "DHT 读取失败";
    Serial.println("DHT 读取失败!");
    return;
  }

  sensorError = false;
  temperature = t;
  humidity = h;
  lastReadTime = millis();

  readWaterSensor();

  Serial.printf(
    "温度=%.1f℃  湿度=%.1f%%  水位=%d\n",
    temperature, humidity, waterValue
  );
}

/* ================= 联动判断 ================= */
void checkLinkage() {
  linkageAlarm = false;
  if (temperature > TEMP_THRESHOLD && waterValue > WATER_THRESHOLD) {
    linkageAlarm = true;
  }
}

/* ================= 发送传感器数据 (MQTT JSON) ================= */
void sendSensorData() {
  readSensorData();
  if (sensorError) return;

  checkLinkage();

  String json = "{";
  json += "\"deviceId\":\"ESP32_001\",";       // 设备ID, 后端 EspEntity.deviceId
  json += "\"temperature\":" + String(temperature, 1) + ",";
  json += "\"humidity\":" + String(humidity, 1) + ",";
  json += "\"water\":" + String(waterValue) + ",";
  json += "\"linkage\":" + String(linkageAlarm ? "true" : "false") + ",";
  json += "\"sendCount\":" + String(sendCount) + ",";
  json += "\"rssi\":" + String(WiFi.RSSI()) + ",";
  json += "\"timestamp\":" + String(millis());
  json += "}";

  if (client.publish(TOPIC_DATA, json.c_str())) {
    sendCount++;
    Serial.println("数据已发送");
  } else {
    Serial.println("发送失败!");
  }
}

/* ================= 发送设备状态 ================= */
void sendStatusUpdate() {
  String status = "{";
  status += "\"deviceId\":\"ESP32_001\",";
  status += "\"sending\":" + String(isSending ? "true" : "false") + ",";
  status += "\"temperature\":" + String(temperature, 1) + ",";
  status += "\"humidity\":" + String(humidity, 1) + ",";
  status += "\"water\":" + String(waterValue) + ",";
  status += "\"linkage\":" + String(linkageAlarm ? "true" : "false") + ",";
  status += "\"rssi\":" + String(WiFi.RSSI()) + ",";
  status += "\"uptime\":" + String(millis() / 1000);
  status += "}";

  client.publish(TOPIC_STATUS, status.c_str());
}

/* ================= 初始化 ================= */
void setup() {
  Serial.begin(115200);
  Serial.println("\n===== 智慧农业 IoT - ESP32 =====");

  dht.begin();

  setupWiFi();

  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);

  // 首次读取
  delay(1000);
  readSensorData();

  Serial.println("初始化完成, 等待MQTT连接...");
}

/* ================= 主循环 ================= */
void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop();

  if (isSending && millis() - lastSendTime > SEND_INTERVAL) {
    sendSensorData();
    lastSendTime = millis();

    if (sendCount % 10 == 0) {
      sendStatusUpdate();
    }
  }

  delay(10);
}

#include <ESP8266WiFi.h>
#include <WiFiClientSecureBearSSL.h>
#include <PubSubClient.h>
#include <DHT.h>
#include <time.h>
#include "secrets.h"

/* WiFi与MQTT凭据由本地secrets.h提供，该文件不会提交到Git */

/* ================= 设备与MQTT配置 ================= */
const char* DEVICE_ID = "device001";
const char* MQTT_SERVER = "38.47.98.235.nip.io";
const int MQTT_PORT = 8883;

String topicControl = String("agri/") + DEVICE_ID + "/control";
String topicData = String("agri/") + DEVICE_ID + "/data";
String topicStatus = String("agri/") + DEVICE_ID + "/status";

/* 项目IoT私有根CA，私钥仅保存在VPS */
static const char IOT_ROOT_CA[] PROGMEM = R"EOF(
-----BEGIN CERTIFICATE-----
MIIB6jCCAZGgAwIBAgIUJvBZubVr+2Ea4ORFQXHc+p1o6M8wCgYIKoZIzj0EAwIw
SzELMAkGA1UEBhMCQ04xGTAXBgNVBAoMEERTLXdvcmtwbGFjZSBJb1QxITAfBgNV
BAMMGERTLXdvcmtwbGFjZSBJb1QgUm9vdCBDQTAeFw0yNjA3MTQwNzAwMDdaFw0z
NjA3MTEwNzAwMDdaMEsxCzAJBgNVBAYTAkNOMRkwFwYDVQQKDBBEUy13b3JrcGxh
Y2UgSW9UMSEwHwYDVQQDDBhEUy13b3JrcGxhY2UgSW9UIFJvb3QgQ0EwWTATBgcq
hkjOPQIBBggqhkjOPQMBBwNCAAT1QGP19RkTIAEeoDelX3edwIEQZPqY83yQfldn
dYmGTXtXtMg8FHgmtHJROOiHvdcoakiAn+r8zTOXtbSSxz0to1MwUTAdBgNVHQ4E
FgQUA8MZQ28Os39nOd0huHvvgaiFQiowHwYDVR0jBBgwFoAUA8MZQ28Os39nOd0h
uHvvgaiFQiowDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNHADBEAiBwdSsI
O2dKY2F15bHVd4vfqraieDnhf8eqV3bKGyDqhgIgZUfKFIRh3Q8dbJcFBsCdJwEP
OyO/R0+wiLYEWzmqNGI=
-----END CERTIFICATE-----
)EOF";

/* ================= 传感器引脚 ================= */
#define DHTPIN 5
#define DHTTYPE DHT11
#define WATER_PIN A0

float TEMP_THRESHOLD = 30.0;
int WATER_THRESHOLD = 300;

/* ================= 全局对象 ================= */
BearSSL::WiFiClientSecure secureClient;
BearSSL::X509List trustAnchor(IOT_ROOT_CA);
PubSubClient client(secureClient);
DHT dht(DHTPIN, DHTTYPE);

/* ================= 状态变量 ================= */
bool isSending = true;
unsigned long lastSendTime = 0;
const unsigned long SEND_INTERVAL = 5000;
unsigned long sendCount = 0;
unsigned long nextMqttReconnectAt = 0;
unsigned long mqttReconnectDelay = 1000;
const unsigned long MQTT_RECONNECT_MAX_DELAY = 60000;

float temperature = 0.0;
float humidity = 0.0;
int waterValue = 0;
bool sensorError = false;
String sensorErrorMessage = "";
bool linkageAlarm = false;

/* ================= WiFi连接 ================= */
void setupWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
    if (++attempts > 40) {
      Serial.println("\nWiFi连接超时，重启...");
      ESP.restart();
    }
  }
  Serial.printf("\nWiFi连接成功，IP=%s\n", WiFi.localIP().toString().c_str());
}

/* ================= TLS时间与证书 ================= */
void setupTls() {
  configTime(0, 0, "pool.ntp.org", "time.cloudflare.com");
  time_t now = time(nullptr);
  int attempts = 0;
  while (now < 1700000000 && attempts++ < 40) {
    delay(500);
    now = time(nullptr);
  }
  if (now < 1700000000) {
    Serial.println("NTP校时失败，稍后重启");
    delay(2000);
    ESP.restart();
  }
  secureClient.setBufferSizes(4096, 4096);
  secureClient.setTrustAnchors(&trustAnchor);
}

/* ================= MQTT回调 ================= */
void callback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (unsigned int i = 0; i < length; i++) {
    message += (char)payload[i];
  }
  if (String(topic) != topicControl) return;

  if (message.equalsIgnoreCase("start")) {
    isSending = true;
    lastSendTime = millis();
    sendCount = 0;
    client.publish(topicStatus.c_str(), "{\"status\":\"started\"}", true);
  } else if (message.equalsIgnoreCase("stop")) {
    isSending = false;
    client.publish(topicStatus.c_str(), "{\"status\":\"stopped\"}", true);
  } else if (message.equalsIgnoreCase("read")) {
    readSensorData();
    sendSensorData();
  } else if (message.equalsIgnoreCase("status")) {
    sendStatusUpdate();
  }
}

/* ================= 非阻塞指数退避重连 ================= */
void ensureMqttConnected() {
  if (client.connected()) return;
  if ((long)(millis() - nextMqttReconnectAt) < 0) return;

  if (!secureClient.connected() && !secureClient.connect(MQTT_SERVER, MQTT_PORT)) {
    char tlsError[160];
    int tlsErrorCode = secureClient.getLastSSLError(tlsError, sizeof(tlsError));
    Serial.printf("TLS握手失败，错误=%d，%s，可用堆=%u，%lu毫秒后重试\n",
      tlsErrorCode, tlsError, ESP.getFreeHeap(), mqttReconnectDelay);
    nextMqttReconnectAt = millis() + mqttReconnectDelay;
    mqttReconnectDelay = min(mqttReconnectDelay * 2, MQTT_RECONNECT_MAX_DELAY);
    return;
  }

  String clientId = String("esp8266-") + DEVICE_ID + "-" + String(ESP.getChipId(), HEX);
  String offlineMsg = String("{\"deviceId\":\"") + DEVICE_ID + "\",\"status\":\"offline\"}";
  bool connected = client.connect(
    clientId.c_str(),
    mqtt_username,
    mqtt_password,
    topicStatus.c_str(),
    1,
    true,
    offlineMsg.c_str()
  );

  if (connected) {
    mqttReconnectDelay = 1000;
    client.subscribe(topicControl.c_str(), 1);
    String onlineMsg = String("{\"deviceId\":\"") + DEVICE_ID
      + "\",\"status\":\"online\",\"ip\":\"" + WiFi.localIP().toString() + "\"}";
    client.publish(topicStatus.c_str(), onlineMsg.c_str(), true);
    Serial.println("MQTT TLS连接成功");
  } else {
    Serial.printf("MQTT连接失败，状态=%d，%lu毫秒后重试\n", client.state(), mqttReconnectDelay);
    char tlsError[160];
    int tlsErrorCode = secureClient.getLastSSLError(tlsError, sizeof(tlsError));
    if (tlsErrorCode != 0) {
      Serial.printf("TLS错误=%d，%s\n", tlsErrorCode, tlsError);
    }
    nextMqttReconnectAt = millis() + mqttReconnectDelay;
    mqttReconnectDelay = min(mqttReconnectDelay * 2, MQTT_RECONNECT_MAX_DELAY);
  }
}

/* ================= 读取水位传感器 ================= */
void readWaterSensor() {
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
  if (millis() - lastReadTime < 2000) return;
  float h = dht.readHumidity();
  float t = dht.readTemperature();
  if (isnan(h) || isnan(t)) {
    sensorError = true;
    sensorErrorMessage = "DHT读取失败";
    return;
  }
  sensorError = false;
  temperature = t;
  humidity = h;
  lastReadTime = millis();
  readWaterSensor();
}

/* ================= 联动判断 ================= */
void checkLinkage() {
  linkageAlarm = temperature > TEMP_THRESHOLD && waterValue > WATER_THRESHOLD;
}

/* ================= 发送传感器数据 ================= */
void sendSensorData() {
  readSensorData();
  if (sensorError || !client.connected()) return;
  checkLinkage();

  String json = String("{\"deviceId\":\"") + DEVICE_ID + "\",";
  json += "\"temperature\":" + String(temperature, 1) + ",";
  json += "\"humidity\":" + String(humidity, 1) + ",";
  json += "\"water\":" + String(waterValue) + ",";
  json += "\"linkage\":" + String(linkageAlarm ? "true" : "false") + ",";
  json += "\"sendCount\":" + String(sendCount) + ",";
  json += "\"rssi\":" + String(WiFi.RSSI()) + ",";
  json += "\"timestamp\":" + String(millis()) + "}";

  if (client.publish(topicData.c_str(), json.c_str())) {
    sendCount++;
  }
}

/* ================= 发送设备状态 ================= */
void sendStatusUpdate() {
  if (!client.connected()) return;
  String status = String("{\"deviceId\":\"") + DEVICE_ID + "\",";
  status += "\"sending\":" + String(isSending ? "true" : "false") + ",";
  status += "\"temperature\":" + String(temperature, 1) + ",";
  status += "\"humidity\":" + String(humidity, 1) + ",";
  status += "\"water\":" + String(waterValue) + ",";
  status += "\"linkage\":" + String(linkageAlarm ? "true" : "false") + ",";
  status += "\"rssi\":" + String(WiFi.RSSI()) + ",";
  status += "\"uptime\":" + String(millis() / 1000) + "}";
  client.publish(topicStatus.c_str(), status.c_str(), true);
}

/* ================= 初始化 ================= */
void setup() {
  Serial.begin(9600);
  Serial.println("\n===== 智慧农业IoT - ESP8266 TLS =====");
  dht.begin();
  setupWiFi();
  setupTls();
  client.setServer(MQTT_SERVER, MQTT_PORT);
  client.setCallback(callback);
  client.setBufferSize(768);
  delay(1000);
  readSensorData();
}

/* ================= 主循环 ================= */
void loop() {
  ensureMqttConnected();
  if (client.connected()) {
    client.loop();
  }
  if (isSending && client.connected() && millis() - lastSendTime > SEND_INTERVAL) {
    sendSensorData();
    lastSendTime = millis();
    if (sendCount > 0 && sendCount % 10 == 0) {
      sendStatusUpdate();
    }
  }
  delay(10);
}

#include <ESP8266WiFi.h>
#include <WiFiClientSecureBearSSL.h>
#include <PubSubClient.h>
#include <DHT.h>
#include <time.h>
#include "secrets.h"
#include "CommandProtocol.h"
#include "SensorSampleState.h"

/* WiFi与MQTT凭据由本地secrets.h提供，该文件不会提交到Git */

/* ================= 设备与MQTT配置 ================= */
const char* DEVICE_ID = "device001";
/* MQTT主机名用于TLS证书校验，IP用于DNS异常时的备用连接。 */
const char* MQTT_SERVER = "node.61153652.xyz";
IPAddress MQTT_SERVER_IP(167, 88, 186, 18);
/* 备用IP连接使用Broker叶子证书指纹固定，避免丢失TLS身份校验。 */
const char* MQTT_CERT_FINGERPRINT = "5E 23 7E 5A CA BE A1 B0 BC 92 E2 01 E4 B6 9A 3E A5 97 47 86";
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
SensorSampleState sampleState;
String sensorErrorMessage = "";
bool linkageAlarm = false;

// 声明命令适配器调用的采样与状态发布接口。
bool readSensorData();
bool sendSensorData();
bool sendStatusUpdate();

struct DeviceCommandAdapter {
  // 返回实时MQTT连接状态，离线时保留待发送回执。
  bool connected() { return client.connected(); }
  // 命令回执不保留，发布失败交由处理器在主循环重试。
  bool publish(const char* payload) { return client.publish(topicStatus.c_str(), payload, false); }
  // 仅操作周期上报状态，当前硬件不包含泵阀执行器。
  bool execute(const char* command) {
    if (strcmp(command, "start") == 0) {
      if (!isSending) lastSendTime = millis();
      isSending = true;
      return true;
    }
    if (strcmp(command, "stop") == 0) {
      isSending = false;
      return true;
    }
    if (strcmp(command, "read") == 0) return sendSensorData();
    if (strcmp(command, "status") == 0) return sendStatusUpdate();
    return false;
  }
};
CommandProtocol commandProtocol(DEVICE_ID);
DeviceCommandAdapter commandAdapter;

/* ================= WiFi连接 ================= */
/* 仅诊断目标网络是否可见，不输出周边 SSID，便于定位 2.4 GHz 或热点兼容性问题。 */
void reportTargetWiFiVisibility() {
  int networkCount = WiFi.scanNetworks();
  bool targetVisible = false;
  int targetRssi = 0;
  int targetEncryption = -1;
  for (int index = 0; index < networkCount; index++) {
    if (WiFi.SSID(index) == ssid) {
      targetVisible = true;
      targetRssi = WiFi.RSSI(index);
      targetEncryption = WiFi.encryptionType(index);
      break;
    }
  }
  Serial.printf("目标WiFi可见=%s，RSSI=%d，加密类型=%d\n",
    targetVisible ? "是" : "否", targetRssi, targetEncryption);
  WiFi.scanDelete();
}

/* 连接配置的 2.4 GHz Wi-Fi，超时后由上层复位并重试。 */
void setupWiFi() {
  WiFi.mode(WIFI_STA);
  reportTargetWiFiVisibility();
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

/* 校时后配置CA信任，失败时重启，禁止跳过TLS身份验证。 */
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

/* 仅处理本设备控制主题，解析、去重与回执由有界协议处理器维护。 */
void callback(char* topic, byte* payload, unsigned int length) {
  if (strcmp(topic, topicControl.c_str()) != 0) return;
  commandProtocol.receive(payload, length, static_cast<uint32_t>(millis()), commandAdapter);
}

/* ================= 非阻塞指数退避重连 ================= */
/* 先用域名+私有CA连接，DNS异常时使用IP+叶子证书指纹备用连接。 */
bool connectMqttTls() {
  secureClient.setTrustAnchors(&trustAnchor);
  if (secureClient.connect(MQTT_SERVER, MQTT_PORT)) return true;

  secureClient.stop();
  secureClient.setFingerprint(MQTT_CERT_FINGERPRINT);
  if (secureClient.connect(MQTT_SERVER_IP, MQTT_PORT)) {
    Serial.println("MQTT TLS备用IP连接成功");
    return true;
  }

  secureClient.stop();
  secureClient.setTrustAnchors(&trustAnchor);
  return false;
}

/* 在主循环中按退避时间恢复TLS与MQTT连接，不阻塞等待重试间隔。 */
void ensureMqttConnected() {
  if (client.connected()) return;
  if ((long)(millis() - nextMqttReconnectAt) < 0) return;

  if (!secureClient.connected() && !connectMqttTls()) {
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

/* 平均十次ADC读数，保持原始ADC单位，不换算为水深。 */
void readWaterSensor() {
  long sum = 0;
  for (int i = 0; i < 10; i++) {
    sum += analogRead(WATER_PIN);
    delay(5);
  }
  waterValue = sum / 10;
}

/* 首次调用立即采样，后续尝试间隔至少两秒；失败或尚未采样时禁止发送初始零值。 */
bool readSensorData() {
  if (!sampleState.beginAttempt(static_cast<uint32_t>(millis()))) return sampleState.isValid();
  float h = dht.readHumidity();
  float t = dht.readTemperature();
  if (isnan(h) || isnan(t)) {
    sampleState.finishAttempt(false);
    sensorErrorMessage = "DHT读取失败";
    return false;
  }
  temperature = t;
  humidity = h;
  readWaterSensor();
  sampleState.finishAttempt(true);
  return true;
}

/* 计算传感器条件标志，仅上报数据，不驱动泵阀。 */
void checkLinkage() {
  linkageAlarm = temperature > TEMP_THRESHOLD && waterValue > WATER_THRESHOLD;
}

/* 采样有效且遥测成功写出时返回true，单次读取不受周期上报开关限制。 */
bool sendSensorData() {
  if (!readSensorData() || !client.connected()) return false;
  checkLinkage();

  String json = String("{\"deviceId\":\"") + DEVICE_ID + "\",";
  json += "\"temperature\":" + String(temperature, 1) + ",";
  json += "\"humidity\":" + String(humidity, 1) + ",";
  json += "\"water\":" + String(waterValue) + ",";
  json += "\"linkage\":" + String(linkageAlarm ? "true" : "false") + ",";
  json += "\"sendCount\":" + String(sendCount) + ",";
  json += "\"rssi\":" + String(WiFi.RSSI()) + ",";
    json += "\"uptimeMillis\":" + String(millis()) + "}";

  if (client.publish(topicData.c_str(), json.c_str())) {
    sendCount++;
    return true;
  }
  return false;
}

/* 上报周期采集开关与采样质量；未取得有效样本的读数明确为null。 */
bool sendStatusUpdate() {
  if (!client.connected()) return false;
  String status = String("{\"deviceId\":\"") + DEVICE_ID + "\",";
  status += "\"sending\":" + String(isSending ? "true" : "false") + ",";
  status += "\"sampleValid\":" + String(sampleState.isValid() ? "true" : "false") + ",";
  status += "\"temperature\":" + (sampleState.isValid() ? String(temperature, 1) : String("null")) + ",";
  status += "\"humidity\":" + (sampleState.isValid() ? String(humidity, 1) : String("null")) + ",";
  status += "\"water\":" + (sampleState.isValid() ? String(waterValue) : String("null")) + ",";
  status += "\"linkage\":" + (sampleState.isValid() ? String(linkageAlarm ? "true" : "false") : String("null")) + ",";
  status += "\"rssi\":" + String(WiFi.RSSI()) + ",";
  status += "\"uptime\":" + String(millis() / 1000) + "}";
  return client.publish(topicStatus.c_str(), status.c_str(), true);
}

/* 初始化传感器、Wi-Fi和TLS，命令处理器使用静态容量且不加载持久化命令。 */
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

/* 维护连接、回执重试和周期上报，停止上报不切断MQTT控制连接。 */
void loop() {
  ensureMqttConnected();
  if (client.connected()) {
    client.loop();
  }
  commandProtocol.retry(static_cast<uint32_t>(millis()), commandAdapter);
  if (isSending && client.connected() && millis() - lastSendTime > SEND_INTERVAL) {
    sendSensorData();
    lastSendTime = millis();
    if (sendCount > 0 && sendCount % 10 == 0) {
      sendStatusUpdate();
    }
  }
  delay(10);
}

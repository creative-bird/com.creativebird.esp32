#include "BluetoothSerial.h"

// Bluetooth Setup
BluetoothSerial SerialBT;

// Motor Pins (L298N Motor Driver)
#define MOTOR_LEFT_FWD 16
#define MOTOR_LEFT_BWD 17
#define MOTOR_RIGHT_FWD 18
#define MOTOR_RIGHT_BWD 19

// PWM Channels für Geschwindigkeitskontrolle
#define PWM_CHANNEL_LEFT 0
#define PWM_CHANNEL_RIGHT 1
#define PWM_FREQ 5000
#define PWM_RESOLUTION 8

// Vacuum Motor Pin
#define VACUUM_PIN 21

// Geschwindigkeit (0-255)
int currentSpeed = 127;
bool vacuumOn = false;

void setup() {
    Serial.begin(115200);

    // Motor Pins als Output
    pinMode(MOTOR_LEFT_FWD, OUTPUT);
    pinMode(MOTOR_LEFT_BWD, OUTPUT);
    pinMode(MOTOR_RIGHT_FWD, OUTPUT);
    pinMode(MOTOR_RIGHT_BWD, OUTPUT);
    pinMode(VACUUM_PIN, OUTPUT);

    // PWM Setup
    ledcSetup(PWM_CHANNEL_LEFT, PWM_FREQ, PWM_RESOLUTION);
    ledcSetup(PWM_CHANNEL_RIGHT, PWM_FREQ, PWM_RESOLUTION);
    ledcAttachPin(MOTOR_LEFT_FWD, PWM_CHANNEL_LEFT);
    ledcAttachPin(MOTOR_RIGHT_FWD, PWM_CHANNEL_RIGHT);

    // Bluetooth starten
    SerialBT.begin("ESP32_Vacuum"); // Name des Bluetooth-Geräts
    Serial.println("Bluetooth gestartet. Warte auf Verbindung...");

    stopMotors();
}

void loop() {
    if (SerialBT.available()) {
        char command = SerialBT.read();
        processCommand(command);
    }

    delay(10);
}

void processCommand(char cmd) {
    Serial.print("Command received: ");
    Serial.println(cmd);

    switch(cmd) {
        case 'F': // Vorwärts
            moveForward();
            break;
        case 'B': // Rückwärts
            moveBackward();
            break;
        case 'L': // Links
            turnLeft();
            break;
        case 'R': // Rechts
            turnRight();
            break;
        case 'X': // Stop
            stopMotors();
            break;
        case 'V': // Vacuum Toggle
            toggleVacuum();
            break;
        case 'S': // Speed Setting
            readSpeed();
            break;
        default:
            // Prüfe ob es eine Nummer ist (für Geschwindigkeit)
            if (cmd >= '0' && cmd <= '9') {
                setSpeedFromDigit(cmd);
            }
            break;
    }
}

void moveForward() {
    digitalWrite(MOTOR_LEFT_BWD, LOW);
    digitalWrite(MOTOR_RIGHT_BWD, LOW);
    ledcWrite(PWM_CHANNEL_LEFT, currentSpeed);
    ledcWrite(PWM_CHANNEL_RIGHT, currentSpeed);
    Serial.println("Moving forward");
}

void moveBackward() {
    ledcWrite(PWM_CHANNEL_LEFT, 0);
    ledcWrite(PWM_CHANNEL_RIGHT, 0);
    digitalWrite(MOTOR_LEFT_BWD, HIGH);
    digitalWrite(MOTOR_RIGHT_BWD, HIGH);
    Serial.println("Moving backward");
}

void turnLeft() {
    digitalWrite(MOTOR_LEFT_BWD, HIGH);
    digitalWrite(MOTOR_RIGHT_BWD, LOW);
    ledcWrite(PWM_CHANNEL_LEFT, 0);
    ledcWrite(PWM_CHANNEL_RIGHT, currentSpeed);
    Serial.println("Turning left");
}

void turnRight() {
    digitalWrite(MOTOR_LEFT_BWD, LOW);
    digitalWrite(MOTOR_RIGHT_BWD, HIGH);
    ledcWrite(PWM_CHANNEL_LEFT, currentSpeed);
    ledcWrite(PWM_CHANNEL_RIGHT, 0);
    Serial.println("Turning right");
}

void stopMotors() {
    ledcWrite(PWM_CHANNEL_LEFT, 0);
    ledcWrite(PWM_CHANNEL_RIGHT, 0);
    digitalWrite(MOTOR_LEFT_BWD, LOW);
    digitalWrite(MOTOR_RIGHT_BWD, LOW);
    Serial.println("Motors stopped");
}

void toggleVacuum() {
    // Lese nächstes Zeichen für 1 oder 0
    if (SerialBT.available()) {
        char state = SerialBT.read();
        if (state == '1') {
            digitalWrite(VACUUM_PIN, HIGH);
            vacuumOn = true;
            Serial.println("Vacuum ON");
        } else if (state == '0') {
            digitalWrite(VACUUM_PIN, LOW);
            vacuumOn = false;
            Serial.println("Vacuum OFF");
        }
    }
}

void readSpeed() {
    String speedStr = "";
    delay(10);
    while (SerialBT.available()) {
        char c = SerialBT.read();
        if (c >= '0' && c <= '9') {
            speedStr += c;
        }
    }

    if (speedStr.length() > 0) {
        int speedPercent = speedStr.toInt();
        currentSpeed = map(speedPercent, 0, 100, 0, 255);
        Serial.print("Speed set to: ");
        Serial.print(speedPercent);
        Serial.print("% (");
        Serial.print(currentSpeed);
        Serial.println("/255)");
    }
}

void setSpeedFromDigit(char digit) {
    int num = digit - '0';
    currentSpeed = map(num * 10, 0, 100, 0, 255);
    Serial.print("Speed set to: ");
    Serial.print(num * 10);
    Serial.println("%");
}
# 🔦 Flashlight — Android App

Фонарик управляемый тройным нажатием кнопки громкости.
Работает на заблокированном экране. Таймер автовыключения.

## Быстрый старт

1. Установи **Android Studio** (Hedgehog или новее)
2. Открой папку `FlashlightApp` через **File → Open**
3. Подожди пока Gradle скачает зависимости (~2 мин)
4. Подключи телефон по USB или запусти эмулятор
5. Нажми **Run ▶**
6. На телефоне: **Настройки → Спец. возможности → Flashlight Service → Включить**

## Жесты

| Действие | Результат |
|---|---|
| 3× кнопка громкости вниз | 🔦 Включить фонарик |
| 4× кнопка громкости вниз | Выключить фонарик |
| Таймер истёк | Выключить автоматически |

## Структура проекта

```
app/src/main/
├── java/com/example/flashlight/
│   ├── MainActivity.kt                   — главный экран, батарея, настройки
│   ├── FlashlightAccessibilityService.kt — сервис: кнопки, таймер, уведомление
│   ├── FlashlightActionReceiver.kt       — выкл из уведомления
│   └── FlashlightServiceHolder.kt        — связь MainActivity ↔ сервис
├── res/
│   ├── layout/activity_main.xml
│   ├── drawable/ (иконки, фон карточек)
│   ├── mipmap-*/  (иконки лаунчера)
│   ├── values/ (colors, themes, strings)
│   └── xml/accessibility_service_config.xml
└── AndroidManifest.xml
```

## Xiaomi / Samsung / Huawei — важно!

Эти производители агрессивно убивают фоновые процессы.
Чтобы сервис жил при заблокированном экране:

**Настройки → Батарея → Управление питанием приложений → Flashlight → Без ограничений**

На Xiaomi MIUI: Настройки → Приложения → Flashlight → Экономия энергии → Без ограничений

## Требования

- Android 6.0+ (API 23)
- Камера со вспышкой
- Android Studio Hedgehog (2023.1.1)+

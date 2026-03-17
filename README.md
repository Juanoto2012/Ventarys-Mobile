# Ventarys AI - Mobile

Ventarys AI es una aplicación de chat moderna para Android inspirada en la interfaz de ChatGPT, diseñada con Jetpack Compose y Material3. Permite chatear con múltiples modelos de lenguaje de alto rendimiento de forma gratuita o utilizando tus propias API Keys.

## ✨ Características

- **Interfaz Moderna**: Diseño minimalista "GPT-style" con soporte completo para modo claro y oscuro.
- **Múltiples Proveedores**:
  - **Ventarys (Free)**: Acceso directo a modelos como Codestral y Llama 3.
  - **Groq Cloud**: Inferencia ultra rápida con modelos Llama 3.1 y Mixtral.
  - **OpenRouter**: Acceso a una vasta librería de modelos gratuitos (`:free`).
  - **Hugging Face**: Conexión con modelos de la comunidad.
- **Configuración Dinámica**:
  - Consulta de modelos en tiempo real desde las APIs oficiales.
  - Gestión segura de API Keys personalizadas.
  - Selector de modelos por proveedor.
- **Historial de Chat**: Guarda tus conversaciones localmente, con opción de swipe para borrar.
- **Markdown Support**: Renderizado de texto enriquecido (negritas, listas, enlaces).
- **Loader Animado**: Indicador de carga de 3 puntos con animaciones fluidas.

## 🚀 Tecnologías

- **Lenguaje**: Kotlin
- **UI**: Jetpack Compose (Material3)
- **Navegación**: Compose Navigation
- **Red**: OkHttp3 & Gson
- **Arquitectura**: MVVM (ViewModel, StateFlow)

## 🛠️ Configuración

1. Clona el repositorio:
   ```bash
   git clone https://github.com/Juanoto2012/Ventarys-Mobile.git
   ```
2. Abre el proyecto en **Android Studio Ladybug** o superior.
3. Sincroniza el proyecto con Gradle.
4. Ejecuta en un dispositivo físico o emulador.

---
Desarrollado por [JNTX Studio](https://github.com/Juanoto2012) - 2024

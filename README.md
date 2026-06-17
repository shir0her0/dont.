# don't

A digital speed bump for Android.

don't helps you become more intentional about how you use your phone by adding a moment of friction before opening distracting apps.

Instead of blocking apps completely, don't encourages you to pause, take a breath, and make a conscious choice before diving into the endless scroll.

## Features

* Monitor selected apps such as Instagram, YouTube, Reddit, X, and more.
* Customizable intervention screen with breathing animation.
* Configurable delay timer.
* Per-app statistics and usage insights.
* Track how often you changed your mind before opening an app.
* Re-intervention support for extended app sessions.
* Multiple visual themes.
* Lightweight and privacy-friendly.
* No accounts, no cloud sync, no ads, no subscriptions.

## Why?

Most app blockers focus on restriction.
don't focuses on awareness.

The goal isn't to stop you from using your phone. The goal is to stop you from using it without thinking.
A few seconds is often all it takes to break an impulsive habit.

## How It Works

1. Select the apps you want to monitor.
2. When you try to open one of those apps, don't displays a short intervention screen.
3. Take a breath, wait a few seconds, and decide whether you still want to continue.
4. Continue to the app or cancel and move on with your day.

That's it.

## Privacy

don't works entirely on-device.
No personal data is collected, transmitted, sold, or shared, and all settings and statistics remain on your device.

## Roadmap

Planned features include:

* Additional themes
* Daily app limits
* Scheduled blocks
* PIN-protected settings
* Enhanced intervention customization
* Improved statistics and insights

## About the Author

Hi, I'm Shireesh.

After accidentally opening Instagram for the 83rd time in a day, I realized my phone needed a speed bump.

That's what don't is.

It won't judge you, lock you out, or lecture you. It simply asks you to pause and make a conscious choice.

What happens after that is between you and your algorithm.

## License

This project is provided as-is and is intended to promote healthier, more intentional phone usage.


This contains everything you need to run your app locally.

View your app in Android Studio:
1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device

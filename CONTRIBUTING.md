# Contributing to Mic

Thank you for your interest in contributing to **Mic**!

## Code of Conduct
We are committed to providing a welcoming, safe, and inclusive environment. Please treat all contributors and users with respect and professionalism.

## Intellectual Property & Rights Preservation
By submitting a Pull Request to this repository, you agree that your contributions will be licensed under the project's [MIT License](LICENSE), with copyright and rights preserved under `FL4K901`.

## Guidelines for Audio DSP & Native Code Contributions

When contributing changes to the audio engine (`android/app/src/main/cpp/` or `android/app/src/main/kotlin/`):

1. **Zero-Heap Allocations on Real-Time Callback:**
   - The native Oboe `onAudioReady` callback must never allocate memory on the heap (no `malloc`, `new`, or dynamic vector resizing in the callback thread).
   - All buffers must be pre-allocated during initialization in `start()`.

2. **Hard-Clipping & Saturation:**
   - Any DSP modification (gain, filters, effects) must strictly clamp output samples to 16-bit integer bounds `[-32768, 32767]` through `softSaturate()` to protect speakers and ears from digital clipping.

3. **Concurrency & Thread Safety:**
   - Modifications to shared engine parameters across JNI must use `std::atomic` or be synchronized via `mStreamMutex`.

4. **Code Quality & Testing:**
   - Run `dart analyze lib/ test/` to verify zero analysis warnings or deprecated API usages.
   - Run `flutter test` to ensure all widget and smoke tests pass.

# android_vendor_hmg-framework

Android 4.2.2 / API 17 기반 HMG Stinger 순정 화면에 음악·사진 패널을 추가하는
QEMU 실험 소스입니다. 커스텀 WindowManager 정책 도우미, 단일 호출을 삽입하는
패치 도구, 패널 앱과 사진 설정 소스를 포함합니다.

현재 구현은 기존 앱의 창 영역을 조정하고 오른쪽 서비스 창을 붙이는 방식입니다.
AOKP 전체 멀티윈도우 포트나 임의의 두 Activity를 실행하는 프레임워크는 아닙니다.
저장소 이름의 `android_vendor_`는 프로젝트 분류이며 AOSP 제품 트리에 바로 넣는
vendor makefile이나 빌드 가능한 전체 OEM 프레임워크를 뜻하지 않습니다.

## 동작

- 기본 800 × 480 화면에서 왼쪽 앱 528px, 오른쪽 음악·사진 패널 272px.
- 비율 유지 시 원래 앱을 528 × 317로 축소하고 터치 좌표를 변환합니다.
- 반응형 모드에서는 앱이 실제 528 × 480 레이아웃을 받습니다.
- 패널에서 위로 스와이프하면 사진·시계, 아래로 스와이프하면 음악을 표시합니다.
- 전체 화면 버튼으로 원래 800 × 480 화면을 복원하고 가장자리 탭으로 다시 분할합니다.
- 다른 앱이 전면에 오거나 패널 프로세스가 종료되면 원래 창 영역을 복원합니다.

## 음악과 휴대폰 지원 상태

`OemMediaSource`는 기존 차량 AVRCP 수신 앱이 내보내는 `IviUxService`를 읽습니다.
Android 전용 휴대폰 앱에 의존하지 않으며 Android와 iPhone 모두 지원하는 것이
목표입니다. **두 종류 휴대폰의 실제 Bluetooth 연결은 아직 검증하지 않았습니다.**

앨범아트는 OEM 미디어 정보에 담긴 로컬 캐시 파일이 있을 때 표시합니다.
현재 펌웨어는 SoundHound 결과의 경로를 전달합니다. **휴대폰으로부터 Bluetooth로
앨범아트를 직접 받는 기능은 아직 구현되지 않았습니다.** 새 곡에 이미지가 없으면
이전 표지를 지웁니다. QEMU의 곡 정보와 그림은 명시적으로 표시된 합성 시험 데이터입니다.

## 소스 구성

| 경로 | 역할 |
| --- | --- |
| `framework/` | `SplitPolicy.constrain()` 창 프레임·스케일 조정 |
| `tools/PatchPolicy.java` | ODEX 분석과 `PhoneWindowManager.layoutWindowLw` 단일 후크 삽입 |
| `src/` | 패널 서비스, 음악 표시, OEM Binder 미디어 어댑터, 설정 |
| `photo-src/` | 기존 사진 대기화면에서 재사용한 가져오기·보관·미리보기 코드 |
| `tests/` | QEMU 계측 앱과 터치 좌표 확인용 Activity |
| `tools/InjectMedia.java`, `tools/UxFixture.java` | QEMU 시험 전용 미디어 주입 및 Binder fixture |
| `verification/` | 이전 QEMU 실행 결과와 공개 소스 재빌드 검증 기록 |

Java와 manifest는 검증에 사용한 기존 실험에서 가져왔습니다. 사진 소스는 당시
빌드에서 적용한 provider authority와 안내 문구 변경을 포함합니다. 외부 프로젝트의
Python 빌드 모듈에 대한 의존성을 제거하고 로컬 경로 설정을 분리했습니다.

## 빌드

검증 환경은 Windows, Python 3.10, JDK 21.0.5, Android Build Tools 34.0.0,
API 17용 `android.jar`, 기존 Android SDK의 baksmali/dexlib2 2.2.1 및 의존 JAR입니다.
다른 dexlib 버전의 호환성은 검증하지 않았습니다. 의존 라이브러리와 원본 펌웨어는
포함하지 않으며 사용자가 별도로 준비해야 합니다.

1. `config.example.json`을 `config.local.json`으로 복사합니다.
2. SDK, JDK `bin`, API 17 jar, dexlib JAR 디렉터리, 원본 `system` 경로를 설정합니다.
   상대 경로는 이 저장소 기준입니다. `system/framework`에 원본 ODEX/JAR들과
   해당 ODEX가 참조하는 boot classpath 의존 파일이 있어야 합니다.
3. 다음 명령을 실행합니다. `python`은 실제 Python 3.10 실행 파일을 가리켜야 합니다.

```powershell
python -X utf8 build.py
python -X utf8 tools/build_tests.py
```

패치 입력 `android.policy.odex`는 다음 SHA-256과 일치해야 합니다.

```text
0ffc2d18f2ecad4af35bdecc7fd6ede0f4afe430733496093546383e7d0f3579
```

다른 입력은 패치를 거부합니다. 빌드 후 deodex 기준본과 비교하여 기존 클래스 중
`PhoneWindowManager` 하나만 변경됐는지, 추가 호출 하나를 제거하면 원래 코드와
같은지 검사합니다. 패널 앱과 테스트 앱은 `signing/`에 새로 생성한 로컬 개발
인증서로 서명합니다. OEM 플랫폼 인증서가 아니며 기존 실험 APK와 서명이 다릅니다.

산출물은 `out/Stinger-Split-API17.apk`, `out/android.policy-SPLIT-QEMU-ONLY.jar`,
`out/build-verification.json`입니다. 테스트 APK와 injector JAR는 별도로 생성하며
시험용 Binder 서비스는 운영 패널 APK에 들어가지 않습니다.

## 검증과 적용 범위

`verification/qemu-2026-11-14.json`은 공개 준비 이전의 QEMU 실행에서 확인한
12개 결과입니다. 분할 프레임, 앨범아트 fixture, 사진 전환, 전체 화면 복원,
터치 좌표 변환, 실제 OEM AllMenus 앱, 프로세스 종료 복원을 포함합니다.
이 기록은 실차, Bluetooth 무선 연결, Android/iPhone 연결 성공의 증거가 아닙니다.
소스 공개 준비 과정의 빌드 검증은 별도 기록으로 구분합니다.

공개 소스 재빌드에서는 정책 DEX와 3개 시험 산출물의 DEX가 기존 결과와 바이트 단위로
일치했습니다. 패널 앱은 기존 증분 빌드에 남아 있던 미사용 익명 클래스 하나를
제외했으며, 나머지 클래스는 baksmali 역어셈블 결과가 동일했습니다. 이번 공개 준비에서
QEMU 실행 시험을 새로 수행하지는 않았습니다.

**순정 ODEX 시스템에 정책 JAR만 교체하면 boot classpath 의존성 때문에 부팅하지
않습니다.** 기존 QEMU 실험은 후속 프레임워크·앱 118개를 deodex한 별도 시스템
사본으로 부팅했습니다. 이 저장소는 그 OEM 바이너리, 시스템 이미지, 개인별 QEMU
실행 환경을 배포하지 않습니다. 빌드는 로컬 산출물만 만들며 기기에 설치하지 않습니다.
실차 배포·복구, 지도·전화·후방카메라 및 전체 앱 호환성은 검증되지 않았습니다.

## 공개 범위

원본 펌웨어, 전체 OEM smali, SDK/JAR/APK 바이너리, 개인 사진, 인증서 및 비밀값은
공개 목록에서 제외했습니다. `audit_public.py`는 Git index를 파일 허용 목록과
대조하고 UTF-8 소스만 포함됐는지 검사합니다. 로컬 설정과 산출물은 Git에서 무시합니다.
프로젝트는 제조사의 공식 프로젝트가 아닙니다. 별도의 재사용 라이선스는 현재
선택하지 않았으며 공개 저장소라는 이유만으로 OEM 자료의 권리가 제공되지는 않습니다.

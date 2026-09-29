pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home'
        ANDROID_HOME = '/Users/kimseongjin/Library/Android/sdk'
        ANDROID_SDK_ROOT = '/Users/kimseongjin/Library/Android/sdk'
        APK_OUTPUT_ROOT = '/Users/kimseongjin/Desktop/jenkins-artifacts/private-agent-android'
    }

    parameters {
        gitParameter(
            name: 'BRANCH_NAME',
            type: 'PT_BRANCH',
            defaultValue: 'main',
            branchFilter: 'origin/(.*)',
            sortMode: 'ASCENDING_SMART',
            selectedValue: 'DEFAULT',
            quickFilterEnabled: true,
            description: '빌드할 Git 브랜치를 선택하세요.'
        )

        choice(
            name: 'BUILD_TYPE',
            choices: ['debug', 'release'],
            description: '빌드 타입을 선택하세요.'
        )
    }

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    def selectedBranch =
                        params.BRANCH_NAME.replaceFirst('^origin/', '')

                    echo "Selected branch: ${selectedBranch}"

                    deleteDir()

                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "*/${selectedBranch}"]],
                        userRemoteConfigs: [[
                            url: 'https://github.com/ksj8624-sudo/private-agent-android.git'
                        ]]
                    ])
                }
            }
        }

        stage('Check Environment') {
            steps {
                sh 'java -version'
                sh './gradlew --version'
            }
        }

        stage('Unit Test') {
            steps {
                script {
                    def variant = params.BUILD_TYPE.capitalize()

                    sh "./gradlew --no-daemon test${variant}UnitTest"
                }
            }
        }

        stage('Assemble') {
            steps {
                script {
                    def variant = params.BUILD_TYPE.capitalize()

                    echo "Build type: ${params.BUILD_TYPE}"
                    sh "./gradlew --no-daemon assemble${variant}"
                }
            }
        }
    }

    post {
        success {
            sh '''
                OUTPUT_DIR="${APK_OUTPUT_ROOT}/${BUILD_TYPE}/build-${BUILD_NUMBER}"
                APK_DIR="app/build/outputs/apk/${BUILD_TYPE}"

                mkdir -p "$OUTPUT_DIR"
                cp "$APK_DIR"/*.apk "$OUTPUT_DIR/"

                echo "APK copied to:"
                echo "$OUTPUT_DIR"
                ls -la "$OUTPUT_DIR"
            '''

            archiveArtifacts(
                artifacts: 'app/build/outputs/apk/**/*.apk',
                fingerprint: true
            )
        }
    }
}
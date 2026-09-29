pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home'
        ANDROID_HOME = '/Users/kimseongjin/Library/Android/sdk'
        ANDROID_SDK_ROOT = '/Users/kimseongjin/Library/Android/sdk'
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
                sh './gradlew --no-daemon testDebugUnitTest'
            }
        }

        stage('Assemble Debug') {
            steps {
                sh './gradlew --no-daemon assembleDebug'
            }
        }
    }
}
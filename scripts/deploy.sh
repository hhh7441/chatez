# 리포지토리 경로 설정
REPOSITORY=/home/ubuntu/app
cd $REPOSITORY

# 애플리케이션 이름 설정
APP_NAME=cahtez

# WAR 파일 이름과 경로 설정
WAR_NAME=$(ls $REPOSITORY/build/libs/ | grep '.war' | tail -n 1)
WAR_PATH=$REPOSITORY/build/libs/$WAR_NAME

# 현재 실행 중인 애플리케이션 프로세스 ID 가져오기
CURRENT_PID=$(pgrep -f $APP_NAME)

# 현재 실행 중인 애플리케이션 종료
if [ -z $CURRENT_PID ]
then
  echo "> 현재 구동중인 애플리케이션이 없으므로 종료하지 않습니다."
else
  echo "> kill -15 $CURRENT_PID"
  sudo kill -15 $CURRENT_PID
  sleep 5
fi

# 새 WAR 파일 배포 및 실행
echo "> $WAR_PATH 배포"
nohup java -jar $WAR_PATH 2>&1 &
REPOSITORY=/home/ubuntu/app
cd $REPOSITORY

APP_NAME=demo
WAR_NAME=$(ls $REPOSITORY/build/libs/ | grep '.war' | tail -n 1)
WAR_PATH=$REPOSITORY/build/libs/$WAR_NAME

CURRENT_PID=$(pgrep -f $APP_NAME)

if [ -z $CURRENT_PID ]
then
  echo "> 현재 구동중인 애플리케이션이 없으므로 종료하지 않습니다."
else
  echo "> kill -15 $CURRENT_PID"
  sudo kill -15 $CURRENT_PID
  sleep 5
fi

echo "> $WAR_PATH 배포"
# WAR 파일을 Tomcat의 webapps 디렉터리로 이동
sudo systemctl stop tomcat
sudo rm -rf /opt/tomcat/tomcat-10/webapps/chatez
sudo rm -rf /opt/tomcat/tomcat-10/webapps/chatez.war
sudo mv $WAR_PATH /opt/tomcat/tomcat-10/webapps/chatez.war
sudo systemctl start tomcat

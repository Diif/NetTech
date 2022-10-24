# Клиент
Отправляет указанный файл серверу по указанному адресу.
Передаваемые аргументы:<br/> [HOSTNAME|IP] [PORT] [FILEPATH]<br/>
## Запуск
### Linux / Mac
./gradlew run --args="[HOSTNAME|IP] [PORT] [FILEPATH]" <br/>
Пример:<br/>```./gradlew run --args="localhost 11112 /memes.png"``` <br/>
### Windows
gradlew.bat run --args="[HOSTNAME|IP] [PORT] [FILEPATH]"

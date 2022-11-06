# Аппа для работы со всякими апишками
Вбиваем место, жмякаем кнопку для поиска, кликаем на нужный результат, кликаем на интересующее место.
## Конфигурация
Заходим в app/src/main/java , вставляем свои ключи в keys.txt
## Запуск
Если возникло непреодолимое желание запустить не через gradle, для логгера нужно прописать VM option:</br>
`-Djava.util.logging.config.file=app/src/main/resources/logging.properties`
### Linux / Mac
./gradlew run
### Windows
gradlew.bat run


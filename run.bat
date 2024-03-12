@echo

call mvn clean

call mvn compile

call mvn exec:exec > log.txt

pause
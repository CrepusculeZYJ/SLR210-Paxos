@echo

call mvn clean
del logs\*.txt
del summary.txt

call mvn compile

call mvn exec:exec > logs/log.txt

call python process.py

summary.txt

@REM pause
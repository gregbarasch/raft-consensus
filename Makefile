compile:
	@mvn clean install

run: compile
	@mvn exec:java

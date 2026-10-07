JAVAC := javac --enable-preview --release 17
JAVA := java --enable-preview
CP := lib/commons-math3-3.6.1.jar
BUILD := build

SRC := $(wildcard src/rates246/*.java)
TEST_SRC := $(wildcard test/rates246/*.java)

.PHONY: all compile test run clean

all: compile

compile:
	mkdir -p $(BUILD)/classes
	$(JAVAC) -cp $(CP) -d $(BUILD)/classes $(SRC)

test: compile
	mkdir -p $(BUILD)/test-classes
	$(JAVAC) -cp $(CP):$(BUILD)/classes -d $(BUILD)/test-classes $(TEST_SRC)
	$(JAVA) -cp $(CP):$(BUILD)/classes:$(BUILD)/test-classes rates246.TestRunner

run: compile
	$(JAVA) -cp $(CP):$(BUILD)/classes rates246.Example

clean:
	rm -rf $(BUILD)

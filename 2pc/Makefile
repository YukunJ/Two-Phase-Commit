# export CLASSPATH=${PWD}:${PWD}/../lib

# define compiler and compiler flag variables
JFLAGS = -g
JC = javac

# set necessary environment variables as well
all: Server.class UserNode.class

%.class: %.java
	$(JC) $(JFLAGS) $*.java

# format command
.PHONY: format
format:
	# clang-format all the source code files
	find *.java | sed 's| |\\ |g' | xargs clang-format -style=google -i

# submission helper command
.PHONY: submit
submit:
	# submit by compressing tar
	tar cvzf ../mysolution.tgz Makefile Server.java UserNode.java

# clean up command
.PHONY: clean
clean:
	# remove all the files with extension .class and executable with tar
	rm -f *.class ../mysolution.tgz
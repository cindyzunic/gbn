Instructions:
1. Compile code by calling:
	make

2. Run nEmulator by calling:
	./nEmulator <emulator's receiving UDP port number in the forward (sender) direction> <receiver’s network address> <receiver’s receiving UDP port number> <emulator's receiving UDP port number in the backward (receiver) direction> <sender’s network address> <sender’s receiving UDP port number> <maximum delay of the link in units of millisecond> <packet discard probability> <verbose-mode> 

3. Run receiver by calling:
	./receiver.sh <hostname for the network emulator> <port number used by the link emulator> <port number used by the receiver> <name of the file>

4. Run sender by calling:
	./sender.sh <hostname for the network emulator> <port number used by the link emulator> <port number used by the sender> <name of the file>
	
Dev Environment: Mac OS Sierra v. 10.12
Java Version 1.8.0_92
Compiler version: GNU-make 3.81

Tested on ubuntu1404-002.student.cs.uwaterloo.ca, ubuntu1404-004.student.cs.uwaterloo.ca and ubuntu1404-006.student.cs.uwaterloo.ca
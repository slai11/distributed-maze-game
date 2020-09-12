# distributed-maze-game
CS5223 Distributed Systems Assignment 1


### Set up
1. Compile maze game
```
cd src/main/java
javac Tracker.java
javac Game.java
```

2. Start rmiregistry
```
start rmiregistry 2001 &
```

3. Run tracker
```
java Tracker 2001 15 10
```


### Launch game
1. Run the game with a 2-character name, e.g. `ab`.
```
java Game 127.0.0.1 2001 ab
```

### Stress testing
1. Compile stress test
```
javac StressTest.java
```

2. Run stress test
```
java StressTest 127.0.0.1 2001 "java Game"
```


### To run with gradle
Requirement:
- Java 11

1. compile
```
./gradlew clean build

```

2. Start rmiregistry at port 2001

3. Run tracker
```
./gradlew -q --console=plain runTracker
```

3. Run game
```
./gradlew runGame --args='127.0.0.1 2001 ab'

```

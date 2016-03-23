## Prerequisites

* Git
* Maven
* JDK 8

## Usage

1. Clone the repo `git@github.com:haus/missing-objects.git`
2. Run `mvn install`
3. Run `mvn exec:java`

After step 3, you should see some output that looks like the following...

[INFO] Scanning for projects...
[INFO]                                                                         
[INFO] ------------------------------------------------------------------------
[INFO] Building missing-objects 1.0-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO] 
[INFO] --- exec-maven-plugin:1.4.0:java (default-cli) @ missing-objects ---
Starting commit thread. Committing every 10 milliseconds...
Commit loop not started yet. Waiting a bit...
Current commit is null
Committing to repo now...
Updated commit is 5d11efcea2b10d882c73a06136317592348fd6bc
Current commit is 5d11efcea2b10d882c73a06136317592348fd6bc
...
Starting fetch thread. Fetching every 10 milliseconds...
Current commit is c12548eb1500d1d9d1c09da7dc9b3a0912417c3a
Fetching from repo now...
Here we are fetching c12548eb1500d1d9d1c09da7dc9b3a0912417c3a into missing-objects/repos/client/test-repo.git
Current commit is c12548eb1500d1d9d1c09da7dc9b3a0912417c3a
Committing to repo now...
Updated commit is 531f3af887d771f388b764d8ccc1bcd7d9fab868
Current commit is 531f3af887d771f388b764d8ccc1bcd7d9fab868
...

The job will run until a MissingObjectException is encountered. Once one
is encountered it will stop all of the threads and shut down the servlet.


Updated commit is 0789a240f3c550a5aa951edc3ff65e83f0e42e9a
Fetching from repo now...
Here we are fetching 0789a240f3c550a5aa951edc3ff65e83f0e42e9a into missing-objects/repos/client/test-repo.git
Missing object encountered. This is awful.
org.eclipse.jgit.errors.MissingObjectException: Missing unknown 0789a240f3c550a5aa951edc3ff65e83f0e42e9a
	at org.eclipse.jgit.internal.storage.file.WindowCursor.open(WindowCursor.java:145)
	at org.eclipse.jgit.lib.ObjectReader.open(ObjectReader.java:226)
	at org.eclipse.jgit.revwalk.RevWalk.parseAny(RevWalk.java:859)
	at org.eclipse.jgit.revwalk.RevWalk.parseCommit(RevWalk.java:772)
	at missingobjects.MissingObjects.getCommit(MissingObjects.java:159)
	at missingobjects.MissingObjects.fetchRepo(MissingObjects.java:187)
	at missingobjects.MissingObjects$Fetcher.run(MissingObjects.java:52)
	at java.lang.Thread.run(Thread.java:745)
Something bad happened: Missing unknown 0789a240f3c550a5aa951edc3ff65e83f0e42e9a
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 2.148 s
[INFO] Finished at: 2016-03-23T17:06:26-07:00
[INFO] Final Memory: 11M/203M
[INFO] ------------------------------------------------------------------------

## Notes

This comes with some fairly aggressive default commit and fetch intervals (10 milliseconds each). These
can be adjusted on lines 79 and 80 of MissingObjects.java.

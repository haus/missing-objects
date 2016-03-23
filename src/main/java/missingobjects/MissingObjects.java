package missingobjects;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class MissingObjects {
    public static class Committer implements Runnable {
        public void run() {
            while (!missingObjectEncountered) {
                try {
                    System.out.println("Current commit is " + latestCommit.get());
                    System.out.println("Committing to repo now...");
                    commitRepo();
                    updateLatestCommit();
                    System.out.println("Updated commit is " + latestCommit.get());
                    isStarted = true;
                    Thread.sleep(commitInterval);
                } catch (InterruptedException | GitAPIException | IOException e) {
                    System.err.println("Something bad happened: " + e.getMessage());
                }
            }
        }
    }

    public static class Fetcher implements Runnable {
        Server myGitServer;
        
        public Fetcher(Server myGitServer) {
            this.myGitServer = myGitServer;
        }

        public void run() {
            while (!missingObjectEncountered) {
                try {
                    System.out.println("Current commit is " + latestCommit.get());
                    System.out.println("Fetching from repo now...");
                    fetchRepo();
                    System.out.println("Updated commit is " + latestCommit.get());
                    Thread.sleep(fetchInterval);
                } catch (MissingObjectException e) {
                    missingObjectEncountered = true;
                    System.err.println("Something bad happened: " + e.getMessage());
                    try {
                        myGitServer.stop();
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                } catch (InterruptedException | GitAPIException | IOException e) {
                    System.err.println("Something bad happened: " + e.getMessage());
                    try {
                        Thread.sleep(fetchInterval);
                    } catch (InterruptedException e2) {
                        System.err.println("Something bad happened: " + e2.getMessage());
                    }
                }
            }
        }
    }


    // Setttings
    static Long commitInterval = 10L;
    static Long fetchInterval = 10L;
    static String repoId = "test-repo";
    static AtomicReference<String> latestCommit = new AtomicReference<String>("");
    static PersonIdent committer = new PersonIdent("Jill Adams", "jill@foo.bar");
    static File serverGitDir;
    static File serverLiveDir;
    static File clientGitDir;
    static boolean isStarted = false;
    static boolean missingObjectEncountered = false;

    public static void updateLatestCommit() throws IOException {
        latestCommit.set(getServerRepository().getRef("refs/heads/master").getObjectId().name());
    }

    public static File bareRepoPath(File baseDir, String repoId) {
        return new File(liveRepoPath(baseDir, repoId) + ".git");
    }

    public static File liveRepoPath(File baseDir, String repoId) {
        return new File(baseDir + "/" + repoId);
    }

    public static File currentDir() throws IOException {
        return new File(".").getCanonicalFile();
    }

    public static File baseDir() throws IOException {
        return new File(currentDir() + "/repos");
    }

    public static File serverBaseDir() throws IOException {
        return new File(baseDir() + "/server");
    }

    public static File clientBaseDir() throws IOException {
        return new File(baseDir() + "/client");
    }

    public static void initializeRepos() throws GitAPIException {
        InitCommand initRepoCommand = new InitCommand();
        initRepoCommand.setDirectory(serverGitDir);
        initRepoCommand.setBare(true);
        initRepoCommand.call();
    }

    public static Repository getServerRepository() throws IOException {
        return getRepository(serverGitDir, serverLiveDir);
    }

    public static Repository getClientRepository() throws IOException {
        return getRepository(clientGitDir);
    }

    public static Repository getRepository(File gitDir, File liveDir) throws IOException {
        RepositoryBuilder curRepo = new RepositoryBuilder();
        curRepo.setGitDir(gitDir);
        curRepo.setWorkTree(liveDir);
        return curRepo.build();
    }

    public static Repository getRepository(File gitDir) throws IOException {
        RepositoryBuilder curRepo = new RepositoryBuilder();
        curRepo.setGitDir(gitDir);
        return curRepo.build();
    }

    public static void commitRepo() throws IOException, GitAPIException {
        Repository serverRepo = getServerRepository();
        Git myGit = Git.wrap(serverRepo);
        myGit.add().addFilepattern(".").call();
        myGit.commit().setMessage("Committing to repo '" + repoId + "'")
                .setAll(true).setAuthor(committer).setCommitter(committer).call();
    }

    public static RevCommit getCommit(Repository repo, String ref) throws IOException {
        ObjectId commitId = repo.resolve(ref);
        if (commitId == null) {
            throw new IllegalStateException("Invalid commit ID");
        }

        RevWalk myRevWalker = new RevWalk(repo);
        RevCommit commitResult = myRevWalker.parseCommit(commitId);
        myRevWalker.dispose();
        return commitResult;
    }

    public static void fetchRepo() throws IOException, GitAPIException {
        String localLatestCommit = latestCommit.get();
        if (clientGitDir.listFiles() != null && clientGitDir.listFiles().length == 0) {
            // If dir is empty, we need to clone first
            System.out.println("Here we are cloning into " + clientGitDir);
            System.out.println("Latest commit is " + localLatestCommit);
            Git.cloneRepository().setBare(true).setRemote("origin").setBranch("master")
                    .setDirectory(clientGitDir).setURI("http://localhost:8080/test-repo.git")
                    .call().close();
        } else {
            // Otherwise we can just fetch it
            System.out.println("Here we are fetching " + localLatestCommit + " into " + clientGitDir);
            Repository clientRepo = getClientRepository();
            Git myGit = Git.wrap(clientRepo);

            if (!localLatestCommit.equals(clientRepo.resolve("master").toString())) {
                myGit.fetch().setRemote("origin").call();
            }
        }

        Repository clientRepo = getClientRepository();

        try {
            RefUpdate updateCommand = clientRepo.updateRef("synced-commit");
            updateCommand.setNewObjectId(getCommit(clientRepo, localLatestCommit));
            updateCommand.update();
        } catch (MissingObjectException e) {
            System.err.println("Missing object encountered. This is awful.");
            e.printStackTrace(System.err);
            throw e;
        }
    }

    public static void makeFirstCommit(File liveDir, String fileName)
            throws IOException, GitAPIException {
        File firstLivefile = new File(liveDir + "/" + fileName);
        firstLivefile.createNewFile();

        commitRepo();
    }

    public static void setUpServerRepos() throws IOException, GitAPIException {
        serverGitDir = bareRepoPath(serverBaseDir(), repoId);
        serverLiveDir = liveRepoPath(serverBaseDir(), repoId);
        clientGitDir = bareRepoPath(clientBaseDir(), repoId);
        serverGitDir.mkdirs();
        serverLiveDir.mkdirs();
        clientGitDir.mkdirs();
        initializeRepos();
        makeFirstCommit(serverLiveDir, "file-to-commit");
    }

    public static Server startGitServlet() throws Exception {
        Server server = new Server(8080);

        ServletHolder holder = new ServletHolder(GitServlet.class);
        holder.setInitParameter("base-path", serverBaseDir().toString());
        holder.setInitParameter("export-all", "1");

        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);

        handler.addServletWithMapping(holder, "/*");
        server.start();
        while (!server.isStarted()) {
            System.out.println("Git web server not up yet. Please hold...");
            Thread.sleep(100);
        }

        return server;
    }

    public static void startCommitThread() {
        System.out.println("Starting commit thread. Committing every " + commitInterval + " milliseconds...");
        (new Thread(new Committer())).start();
    }

    public static void startFetchThread(Server myGitServer) {
        System.out.println("Starting fetch thread. Fetching every " + commitInterval + " milliseconds...");
        (new Thread(new Fetcher(myGitServer))).start();
    }

    public static void waitForStart() throws InterruptedException {
        while (!isStarted) {
            System.out.println("Commit loop not started yet. Waiting a bit...");
            Thread.sleep(1000);
        }
    }

    public static void main( String[] args ) throws Exception {
        // Jetty is very chatty otherwise
        Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.ERROR);

        setUpServerRepos();
        Server myGitServer = startGitServlet();
        startCommitThread();

        // Here we wait for the commit thread to have made its first commit
        waitForStart();
        startFetchThread(myGitServer);
    }
}

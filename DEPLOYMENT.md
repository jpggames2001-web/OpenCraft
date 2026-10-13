# Build, commit and publish the project

The cloud checkout is already a Git repository with an `origin` for `jpggames2001-web/OpenCraft`. The prebuilt package is tracked in `dist/` for direct download from GitHub. The steps below describe how to publish future changes.

## Existing checkout

Run validation before committing:

```bash
cd /workspace/OpenCraft
./build.sh
./run.sh --headless-smoke --data-dir /workspace/opencraft-validation
git status --short
git diff --check
```

Create a branch, add the implementation, and commit:

```bash
git switch -c opencraft-implementation
git add .gitignore .github pom.xml src build.sh run.sh build.cmd run.cmd README.md FIDELITY.md VALIDATION.md DEPLOYMENT.md
git add dist/SHA256SUMS
git add -f dist/opencraft-0.1.0.jar
git commit -m "Implement Java 21 OpenCraft voxel game and validation suite"
```

Push the branch to the existing GitHub repository:

```bash
git push -u origin opencraft-implementation
```

The GitHub Actions workflow will build and test the branch on Linux and Windows and attach the packaged JAR as a workflow artifact. Review the fidelity limitations before presenting a release as equivalent to Minecraft 1.0.

Git needs your configured author identity for the commit and write authorization for the push. Use the environment's existing Git authentication; no secret values belong in the source or these scripts.

## Starting from an exported folder without `.git`

Only use these initialization commands if there is no existing Git repository:

```bash
git init
git branch -M main
git remote add origin https://github.com/jpggames2001-web/OpenCraft.git
git add .
git commit -m "Implement Java 21 OpenCraft voxel game and validation suite"
git push -u origin main
```

If the remote already contains history, use its existing checkout and the branch workflow above. Do not force-push over existing commits.

## Running the packaged application

Linux:

```bash
./run.sh
```

Windows:

```bat
run.cmd
```

Or download the committed `dist/opencraft-0.1.0.jar` from GitHub and run it with a Java 21 runtime:

```text
java -jar opencraft-0.1.0.jar
```

There is no hosted web service to deploy: OpenCraft is a native desktop OpenGL application.

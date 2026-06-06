#define _GNU_SOURCE
#include <unistd.h>
#include <grp.h>

extern char **environ;

int main(int argc, char *argv[]) {
    if (argc < 2) return 1;
    gid_t groups[] = {2000, 3003}; // shell, inet
    setgroups(2, groups);
    setresgid(2000, 2000, 2000);
    setresuid(2000, 2000, 2000);
    execve(argv[1], argv + 1, environ);
    return 1;
}

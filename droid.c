#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>

#define SOCKET_NAME "android-bridge"

int main(int argc, char *argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Usage: droid <command> [args...]\n");
        return 1;
    }

    int sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (sock < 0) {
        perror("socket");
        return 1;
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0'; // Abstract namespace
    strcpy(addr.sun_path + 1, SOCKET_NAME);

    if (connect(sock, (struct sockaddr*)&addr, sizeof(sa_family_t) + 1 + strlen(SOCKET_NAME)) < 0) {
        perror("connect");
        return 1;
    }

    // Send command
    for (int i = 1; i < argc; i++) {
        write(sock, argv[i], strlen(argv[i]));
        if (i < argc - 1) {
            write(sock, " ", 1);
        }
    }
    write(sock, "\n", 1);

    // Read response
    char buf[1024];
    int n;
    while ((n = read(sock, buf, sizeof(buf) - 1)) > 0) {
        buf[n] = '\0';
        printf("%s", buf);
    }

    close(sock);
    return 0;
}

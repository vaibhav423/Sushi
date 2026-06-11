#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>

#define SOCKET_NAME "android-bridge"

static const char b64t[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static void b64enc(const unsigned char *in, size_t len, char *out) {
  for (size_t i = 0, j = 0; i < len; i += 3) {
    unsigned a = in[i], b = i+1<len?in[i+1]:0, c = i+2<len?in[i+2]:0;
    out[j++] = b64t[a>>2];
    out[j++] = b64t[((a&3)<<4)|(b>>4)];
    out[j++] = i+1<len?b64t[((b&15)<<2)|(c>>6)]:'=';
    out[j++] = i+2<len?b64t[c&63]:'=';
    out[j] = 0;
  }
}

static char *read_file(const char *path, size_t *len) {
  FILE *f = fopen(path, "rb");
  if (!f) return NULL;
  struct stat st;
  stat(path, &st);
  char *buf = malloc(st.st_size + 1);
  *len = fread(buf, 1, st.st_size, f);
  fclose(f);
  buf[*len] = 0;
  return buf;
}

static void send_and_recv(int sock, const char *cmd) {
  write(sock, cmd, strlen(cmd));
  write(sock, "\n", 1);
  char buf[4096];
  int n;
  while ((n = read(sock, buf, sizeof(buf) - 1)) > 0) {
    buf[n] = 0;
    printf("%s", buf);
    fflush(stdout);
  }
}

int main(int argc, char *argv[]) {
  if (argc < 2 || (strcmp(argv[1], "-h") == 0)) {
    fprintf(stderr,
      "Usage: droid <command> [args...]\n"
      "       droid -f <file.java>\n"
      "       droid - < file.java\n"
      "\nCommands:\n"
      "  hello                  sanity check\n"
      "  java <base64>          execute base64-encoded BeanShell code\n"
      "  java-raw <code>        execute raw inline BeanShell code\n"
      "  -f <file>              execute BeanShell from file\n"
      "  -                      execute BeanShell from stdin\n"
      "\nBeanShell via java-raw:\n"
      "  droid java-raw '2+2'       evaluate expression\n"
      "  droid java-raw 'br.log(x)' print to daemon log\n"
      "  droid -f examples/*.java   run example scripts\n");
    return 1;
  }

  int sock = socket(AF_UNIX, SOCK_STREAM, 0);
  if (sock < 0) { perror("socket"); return 1; }

  struct sockaddr_un addr;
  memset(&addr, 0, sizeof(addr));
  addr.sun_family = AF_UNIX;
  addr.sun_path[0] = 0;
  strcpy(addr.sun_path + 1, SOCKET_NAME);

  if (connect(sock, (struct sockaddr*)&addr,
      sizeof(sa_family_t) + 1 + strlen(SOCKET_NAME)) < 0) {
    perror("connect");
    return 1;
  }

  if (strcmp(argv[1], "-f") == 0 || strcmp(argv[1], "-") == 0) {
    char *content;
    size_t len;
    if (argv[1][1] == 'f') {
      if (argc < 3) { fprintf(stderr, "Usage: droid -f <file.java>\n"); return 1; }
      content = read_file(argv[2], &len);
    } else {
      size_t cap = 4096;
      content = malloc(cap);
      len = 0;
      int c;
      while ((c = getchar()) != EOF) {
        if (len + 1 >= cap) { cap *= 2; content = realloc(content, cap); }
        content[len++] = c;
      }
      content[len] = 0;
    }
    if (!content || !len) { fprintf(stderr, "error: empty or unreadable\n"); return 1; }

    size_t b64len = ((len + 2) / 3) * 4 + 1;
    char *b64out = malloc(b64len);
    b64enc((unsigned char*)content, len, b64out);
    free(content);

    size_t cmdlen = 5 + b64len;
    char *cmd = malloc(cmdlen);
    snprintf(cmd, cmdlen, "java %s", b64out);
    free(b64out);
    send_and_recv(sock, cmd);
    free(cmd);
  } else {
    for (int i = 1; i < argc; i++) {
      write(sock, argv[i], strlen(argv[i]));
      if (i < argc - 1) write(sock, " ", 1);
    }
    write(sock, "\n", 1);
    char buf[4096];
    int n;
    while ((n = read(sock, buf, sizeof(buf) - 1)) > 0) {
      buf[n] = 0;
      printf("%s", buf);
      fflush(stdout);
    }
  }

  close(sock);
  return 0;
}

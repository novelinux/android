#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>

int main(int argc, char *argv[]) {
  const char *s = "hello world";
  uint32_t len = strlen(s);
  int times = 0;
  int fd = open("./test_file" , O_RDWR|O_CREAT|O_APPEND, S_IRWXU|S_IRWXG|S_IRWXO);
  if (fd < 0) {
    printf("open failed!\n");
    return -1;
  }

  while (1) {
    sleep(5);
    write(fd , s , strlen(s));
    len += strlen(s);
    printf("[%d]: [%d] bytes write\n" , times, len);
    times++;
  }

  return 0;
}

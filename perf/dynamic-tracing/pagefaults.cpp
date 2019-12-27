#include <stdio.h>
#include <string.h>

#include <sys/mman.h>

int main(int argc, char *argv[]) {
  if (!strncmp(argv[1], "hello", 5)) {
    printf("Hello ");
  }

  if (!strncmp(argv[2], "world", 5)) {
    printf("World.\n");
  }
  printf("\n");

  int rc = mlockall(0);
  printf("mlockall(0) = %s\n", strerror(rc));

  rc = mlockall(MCL_CURRENT);
  printf("mlockall(MCL_CURRENT) = %s\n", strerror(rc));

  getchar();

  return 0;
}

#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>

#include <string>

static int readahead_file(const std::string& filename, bool fully) {
  int fd = open(filename.c_str(), O_RDONLY);
  if (fd == -1) {
    return -1;
  }

  if (posix_fadvise(fd, 0, 0, POSIX_FADV_WILLNEED)) {
    return -1;
  }
  if (readahead(fd, 0, std::numeric_limits<size_t>::max())) {
    return -1;
  }
  if (fully) {
    char buf[BUFSIZ];
    ssize_t n;
    while ((n = read(fd, &buf[0], sizeof(buf))) > 0) {
    }
    if (n != 0) {
      return -1;
    }
  }
  printf("success.\n");
  return 0;
}

int main(int argc, char *argv[]) {
  return readahead_file(std::string(argv[1]), true);
}

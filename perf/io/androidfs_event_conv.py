#!/usr/bin/env python

import logging
import os
import sys

logger = logging.getLogger(__name__)

def RemoveAllStalePycFiles(base_dir):
    """Scan directories for old .pyc files without a .py file and delete them."""
  for dirname, _, filenames in os.walk(base_dir):
    if '.git' in dirname:
      continue
    for filename in filenames:
        root, ext = os.path.splitext(filename)
      if ext != '.pyc':
        continue

      pyc_path = os.path.join(dirname, filename)
      py_path = os.path.join(dirname, root + '.py')

      try:
        if not os.path.exists(py_path):
            os.remove(pyc_path)
      except OSError:
          # Wrap OS calls in try/except in case another process touched this file.
        pass

    try:
        os.removedirs(dirname)
    except OSError:
        # Wrap OS calls in try/except in case another process touched this dir.
      pass

def main_impl(args):
  trace_file = open()

def main():
  main_impl(sys.argv)

if __name__ == '__main__':
  RemoveAllStalePycFiles(os.path.dirname(__file__))
  main()

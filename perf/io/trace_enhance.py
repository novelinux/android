#!/usr/bin/python2.7

trace_file = open("1.html")
new_trace_file = open("1_io.html", "w+")
read_seq = open("read_seq_512", "w+")

read_record = {}
#write_record = {}

for line in trace_file.readlines():
    if "android_fs_dataread_start" in line:
        parts = line.partition(': android_fs_dataread_start:')
        pid_info = (parts[0].split('['))[0]
        read_info = parts[2].split(',')

        if len(read_info) < 7:
            print line
            continue

        filename = read_info[0].lstrip('entry_name ')
        offset = read_info[1].lstrip('offset ')
        size = read_info[2].lstrip("bytes ")
        pid = read_info[4].lstrip('pid ')
        inode = read_info[6].lstrip('ino ').strip('\n')

        new_start_line = parts[0] + ": tracing_mark_write: B|" + pid + '|read: '\
                   + filename + ", offset: " + offset + ", size: " + size + '\n'

        read_key = inode + ":" + offset
        read_kv = {read_key : pid_info}
        read_record.update(read_kv)

        #print new_start_line
        new_trace_file.write(new_start_line)

        new_end_line = parts[0] + ": tracing_mark_write: E|" + pid + '\n'
        new_trace_file.write(new_end_line)

        if ' id.article.news-'  in line:
            if  'com.ss.android.article.news' in filename:
                read_seq.write('/data' + filename + "," + offset + "," + size + '\n')
            else:
                read_seq.write('/system' + filename + "," + offset + "," + size + '\n')
        continue

    if "android_fs_dataread_end" in line:
        parts = line.partition(': android_fs_dataread_end:')
        read_info = parts[2].split(',')

        if len(read_info) < 2:
            print line
            continue

        inode = read_info[0].lstrip('ino ')
        offset = read_info[1].lstrip('offset ')
        read_key = inode + ":" + offset

        if read_record.has_key(read_key):
            start_pid_info = read_record[read_key]
        else:
            continue
        pid = (start_pid_info.split('-'))[1].split(' ')[0]

        new_end_line = start_pid_info + '[' + (parts[0].split('['))[1] + ": tracing_mark_write: E|" + pid + '\n'

        del read_record[read_key]

        #print new_end_line
        #new_trace_file.write(new_end_line)
        continue

    new_trace_file.write(line)


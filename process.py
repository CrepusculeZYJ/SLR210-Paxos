import re
# 定义日志文件的路径和输出文件的路径
log_file_path = "logs/log.txt"
info_output_path = "logs/info.txt"
debug_output_path = "logs/debug.txt"
summary_output_path = "summary.txt"

# 初始化计数器用于跟踪INFO条目
info_count = 0

# 编译正则表达式模式
pattern1 = re.compile(r'\[INFO\].*\[akka:\/\/system\/user\/Actor(\d+)\].*DECIDE from.*proposal \[(\d+)\]')
pattern2 = re.compile(r'\[INFO\].*\[akka:\/\/system\/user\/Actor\d+\].*Process \[(\d+)\].*value \[(\d+)\] ballot.*: (\d+)ms')

# 判断是否一致
decide_value = -1
decide_value_write = False
concurrency = True

# 重复的节点不输出
node_number = set()
node_info = {}

# 打开输出文件
with open(info_output_path, 'w') as info_file, open(debug_output_path, 'w') as debug_file, open(summary_output_path, 'w') as summary_file:
    # 打开并读取日志文件
    with open(log_file_path, 'r') as log_file:
        for line in log_file:
            # 忽略前八个INFO条目，然后写入info.txt
            if line.startswith('[INFO]') and info_count >= 8:
                info_file.write(line)
                match1 = pattern1.search(line)
                match2 = pattern2.search(line)
                if match1:
                    actor =int(match1.group(1))
                    value = match1.group(2)                        
                    if (decide_value_write == False and decide_value == -1):
                        decide_value = value
                        decide_value_write = True
                    if (decide_value != value):
                        # summary_file.write(f"NODE[{actor}], VALUE[{value}] <------------------ uncheck\n")
                        node_info[actor] = f"NODE\t[{actor}]\tVALUE\t[{value}]\t<------------------ uncheck\n"
                        concurrency = False
                    else:
                        if actor not in node_number:
                            # summary_file.write(f"NODE[{actor}], VALUE[{value}]\n")
                            node_info[actor] = f"NODE\t[{actor}]\tVALUE\t[{value}]\n"
                    node_number.add(actor)
                elif match2:
                    process = int(match2.group(1))
                    value = match2.group(2)
                    time = match2.group(3)
                    if (decide_value_write == False and decide_value == -1):
                        decide_value = value
                        decide_value_write = True
                    if decide_value != value :
                        # summary_file.write(f"LEADER[{process}], VALUE[{value}], TIME[{time}ms] <---------- uncheck\n")
                        node_info[process] = f"LEADER\t[{process}]\tVALUE\t[{value}]\tTIME[{time}ms]\t<---------- uncheck\n"
                        concurrency = False
                    else:
                        if process not in node_number:
                            # summary_file.write(f"LEADER[{process}], VALUE[{value}], TIME[{time}ms]\n")
                            node_info[process] = f"LEADER\t[{process}]\tVALUE\t[{value}]\tTIME[{time}ms]\n"
                    node_number.add(process)
            elif line.startswith('[DEBUG]'):
                debug_file.write(line)
            # 更新INFO条目的计数
            if line.startswith('[INFO]'):
                info_count += 1
    for node_line in sorted(node_info):
        summary_file.write(node_info[node_line])
    if concurrency == False:
        summary_file.write(f"**************[CONCURRENCY ERREOR]*************\n")

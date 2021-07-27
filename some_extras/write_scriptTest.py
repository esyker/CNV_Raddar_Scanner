#!/usr/bin/env python
# coding: utf-8

import sys
import os
from random import shuffle
import numpy as np

# # Function to make queries for Solver or WebServer
# ### Command to run Solver:  
# 
# java pt.ulisboa.tecnico.cnv.solver.SolverMain -d -s PROGRESSIVE_SCAN -w 512 -h 512 -x0 0 -x1 50 -y0 0 -y1 50 -i 'datasets/SIMPLE_VORONOI_512x512_1.png' -yS 25 -xS 25 -o out/
# 
# ### Saves to file scriptTest.sh
# 
# to run file in Linux use:
# . scriptTest.sh &> output.txt

# ### data

def data():
    maps=['datasets/SIMPLE_VORONOI_1024x1024_1.png',
    'datasets/SIMPLE_VORONOI_1024x1024_2.png',
    'datasets/SIMPLE_VORONOI_1024x1024_3.png',
    'datasets/SIMPLE_VORONOI_1024x1024_4.png',
    'datasets/SIMPLE_VORONOI_1024x1024_5.png',
    'datasets/SIMPLE_VORONOI_1024x1024_6.png',
    'datasets/SIMPLE_VORONOI_1024x1024_7.png',
    'datasets/SIMPLE_VORONOI_1024x1024_8.png',
    'datasets/SIMPLE_VORONOI_2048x2048_1.png',
    'datasets/SIMPLE_VORONOI_2048x2048_2.png',
    'datasets/SIMPLE_VORONOI_2048x2048_3.png',
    'datasets/SIMPLE_VORONOI_2048x2048_4.png',
    'datasets/SIMPLE_VORONOI_2048x2048_5.png',
    'datasets/SIMPLE_VORONOI_2048x2048_6.png',
    'datasets/SIMPLE_VORONOI_2048x2048_7.png',
    'datasets/SIMPLE_VORONOI_2048x2048_8.png',
    'datasets/SIMPLE_VORONOI_512x512_1.png',
    'datasets/SIMPLE_VORONOI_512x512_2.png',
    'datasets/SIMPLE_VORONOI_512x512_3.png',
    'datasets/SIMPLE_VORONOI_512x512_4.png']

    strategies = ['GRID_SCAN','PROGRESSIVE_SCAN','GREEDY_RANGE_SCAN']

    width=[1024,1024,1024,1024,1024,1024,1024,1024,2048,2048,2048,2048,2048,2048,2048,2048,512,512,512,512]
    height =width

    return maps, strategies, width, height

def create_file(folder, max_files):
    maps, strategies, width, height = data()
    start = 'java pt.ulisboa.tecnico.cnv.solver.SolverMain -s '

    # java pt.ulisboa.tecnico.cnv.solver.SolverMain -d -s PROGRESSIVE_SCAN -w 512 -h 512 -x0 0 -x1 50 -y0 0 -y1 50 -i 'datasets/SIMPLE_VORONOI_512x512_1.png' -yS 25 -xS 25 -o /out

    # ## Code to run the file
    my_list=[]
    window_span_x_array = np.random.uniform(0,1,10)
    window_span_y_array = np.random.uniform(0,1,10)

    for i in range(len(maps)):
        for window_span_x in window_span_x_array:
            for window_span_y in window_span_y_array:
                xmin=0
                xmax=width[i]
                ymin=0
                ymax=height[i]
                delta_x=int(window_span_x*xmax)
                delta_y=int(window_span_y*ymax)

                for x0 in range(xmin,xmax,delta_x):
                    for y0 in range(ymin,ymax,delta_y):
                        for strategy in strategies:

                            x1 = x0+delta_x
                            y1 = y0+delta_y
                            xs = int((x0+x1)/2)
                            ys = int((y0+y1)/2)
                            window = ' -w ' + str(width[i]) + ' -h ' + str(height[i]) + f' -x0 {x0} -x1 {x1} -y0 {y0} -y1 {y1}'
                            start_point=f' -xS {xs} -yS {ys}'
                            cmd = start + strategy + window + ' -i ' + maps[i] + start_point + ' -o out/ '

                            my_list.append(cmd)

    print(f'Total number of tests generated: {len(my_list)}')

    if max_files > 0:
        shuffle(my_list)
        del my_list[max_files:]

    # update file
    file_path=f'{folder}\\scriptTest.sh'
    f = open(file_path, "w")
    for line in my_list:
        f.write(line + '\n')
    f.close()

    print(f'File {file_path} updated.')

def http_queries(IP,folder, max_files):
    maps, strategies, width, height = data()
    port_method= ':8000/scan?'

    my_list=[]
    window_span_x_array = np.random.uniform(0,1,10)
    window_span_y_array = np.random.uniform(0,1,10)

    for i in range(len(maps)):
        for window_span_x in window_span_x_array:
            for window_span_y in window_span_y_array:
                xmin=0
                xmax=width[i]
                ymin=0
                ymax=height[i]
                delta_x=int(window_span_x*xmax)
                delta_y=int(window_span_y*ymax)

                for x0 in range(xmin,xmax,delta_x):
                    for y0 in range(ymin,ymax,delta_y):
                        for strategy in strategies:

                            x1 = x0+delta_x
                            y1 = y0+delta_y
                            xs = int((x0+x1)/2)
                            ys = int((y0+y1)/2)
                            window = 'w=' + str(width[i]) + '&h=' + str(height[i]) + f'&x0={x0}&x1={x1}&y0={y0}&y1={y1}'
                            start_point=f'&xS={xs}&yS={ys}'
                            current_map = maps[i].replace('datasets/','')
                            cmd = f'http://{IP}{port_method}{window}{start_point}&s={strategy}&i={current_map}'
                            my_list.append(cmd)

    print(f'Total number of tests generated: {len(my_list)}')

    if max_files > 0:
        shuffle(my_list)
        del my_list[max_files:]

    # update file
    file_path=f'{folder}\\queries_links.txt'
    f = open(file_path, "w")
    for line in my_list:
        f.write(line + '\n')
    f.close()

    print(f'File {file_path} updated.')


if __name__ == "__main__":


   
    try:
        if os.path.exists(sys.argv[2]):
            print(f'Folder selected: {sys.argv[2]}')
            folder = sys.argv[2]
        else:
            folder = os.getcwd()
    except:   # save in current directory
        folder = os.getcwd()

    try:
        max_files = int(sys.argv[3])
        print(f'Number of files in the output: {max_files}')
    except Exception as e:
        max_files = 0

    if sys.argv[1]=='solver':  
        create_file(folder, max_files)
    elif sys.argv[1]=='server':  
        IP= input("Enter the IP address of WebServer:")
        http_queries(IP, folder, max_files)
    else:
        print("Please indicate first option as solver or server")






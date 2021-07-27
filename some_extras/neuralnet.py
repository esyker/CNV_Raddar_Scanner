# Import relevant dependencies

import math
import matplotlib.pyplot as plt
import numpy as np
from numpy.random import seed
import os

seed(1)
import pandas as pd
import statsmodels.api as sm
import statsmodels.formula.api as smf
import tensorflow

tensorflow.random.set_seed(1)
from tensorflow.python.keras.layers import Dense
from tensorflow.keras.layers import Dropout
from tensorflow.python.keras.models import Sequential
from tensorflow.python.keras.wrappers.scikit_learn import KerasRegressor
from sklearn.metrics import mean_absolute_error
from sklearn.metrics import r2_score
from sklearn.metrics import mean_squared_error
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import MinMaxScaler
import sklearn as skl
import seaborn as sns

def load_variables():
    # Importing data
    colnames = ["id",
                "strategy",
                "width",
                "height",
                "x0", "x1", "y0", "y1",
                "map", "xS", "yS", "folder",
                "timestamp",
                "dyn_method_count",
                "dyn_bb_count",
                "dyn_instr_count",
                "instr_per_bb",
                "instr_per_method",
                "bb_per_method"]

    dataset = pd.read_csv('data_20210518.csv', names=colnames)

    # Clear some data that was looking strange (maybe due to overflow of type double):
    dataset = dataset[dataset.dyn_method_count > 0]
    dataset = dataset[dataset.dyn_bb_count > 0]
    dataset = dataset[dataset.dyn_instr_count > 0]

    list_to_pop = ["folder", "timestamp", "instr_per_bb", "instr_per_method", "bb_per_method"]
    for element in list_to_pop:
        dataset.pop(element)

    dataset['map'] = dataset['map'].str.replace('datasets/', '')

    print(dataset.head())

    # Define dependent Vars
    strategy_cat = dataset.strategy.astype("category").cat.codes
    strategy_cat = pd.Series(strategy_cat)

    map_cat = dataset.map.astype("category").cat.codes
    map_cat = pd.Series(map_cat)

    x1 = np.column_stack((dataset.loc[:, "width":"y1"].values, dataset.loc[:, "xS":"yS"].values, strategy_cat, map_cat))

    dataset['area'] = dataset['width']*dataset['height']
    dataset['search_area'] = (dataset.x1-dataset.x0)*(dataset.y1-dataset.y0)
    dataset['strategy_cat'] = strategy_cat
    dataset['map_cat'] = map_cat


    return dataset


def nn_model(x1, y1, y1_name):
    # Split data into test & train
    X_train, X_val, y_train, y_val = train_test_split(x1, y1, test_size=0.2, random_state=42)

    # Normalize the variables
    y_train = np.reshape(y_train, (-1, 1))
    y_val = np.reshape(y_val, (-1, 1))

    scaler_x = MinMaxScaler()
    scaler_y = MinMaxScaler()
    print(scaler_x.fit(X_train))

    xtrain_scale = scaler_x.transform(X_train)
    print(scaler_x.fit(X_val))

    xval_scale = scaler_x.transform(X_val)
    print(scaler_y.fit(y_train))

    ytrain_scale = scaler_y.transform(y_train)
    print(scaler_y.fit(y_val))
    yval_scale = scaler_y.transform(y_val)

    num_neurons_l1 = np.shape(x1)[1]

    model = Sequential()
    model.add(Dense(num_neurons_l1, input_dim=num_neurons_l1, kernel_initializer='normal', activation='relu'))
    model.add(Dense(num_neurons_l1 ** 3 * 3, activation='relu'))
    model.add(Dense(num_neurons_l1 ** 3 * 6, activation='relu'))
    model.add(Dense(num_neurons_l1 ** 3 * 3, activation='relu'))
    model.add(Dense(1, activation='linear'))
    model.summary()

    model.compile(loss='mse', optimizer='adam', metrics=['mse', 'mae'])
    history = model.fit(xtrain_scale, ytrain_scale, epochs=50, batch_size=200, verbose=1, validation_split=0.2)

    model.save('complexity.h5')
    predictions = model.predict(xval_scale)

    print(history.history.keys())
    # "Loss"
    predictions = scaler_y.inverse_transform(predictions)

    plot = True
    if plot:
        fig, (ax1, ax2) = plt.subplots(1, 2)
        ax1.plot(history.history['loss'])
        ax1.plot(history.history['val_loss'])
        ax1.set_title('model loss')
        ax1.set(ylabel='loss', xlabel='epoch')
        ax1.legend(['train', 'validation'], loc='upper left')


        ax2.plot(y_val)
        ax2.plot(predictions)
        ax2.set_title('y_val vs predictions')
        ax2.set(ylabel=y1_name, xlabel='query')
        plt.legend(['y_val', 'predictions'], loc='upper left')
        plt.show()

    mae = mean_absolute_error(y_val, predictions)
    mse = math.sqrt(mean_squared_error(y_val, predictions))
    r2 = skl.metrics.r2_score(y_val, predictions)

    return mae, mse, r2


def run():

    dataset = load_variables()

    # this subset of results don't give good results
    #x1 = np.column_stack((dataset.loc[:, 'search_area'].values, dataset.loc[:, 'area'].values,
    #                      dataset.loc[:, "xS":"yS"].values, dataset.strategy_cat.values,
    #                      dataset.map_cat.values))

    x1 = np.column_stack((dataset.loc[:, "width":"y1"].values, dataset.loc[:, "xS":"yS"].values,
                          dataset.strategy_cat.values, dataset.map_cat.values))

    # Define target variable
    output_vars = ["dyn_method_count", "dyn_bb_count", "dyn_instr_count"]
    #output_vars = ['dyn_instr_count']

    results = []
    for target in output_vars:
        y1 = dataset[target].values
        mae, mse, r2 = nn_model(x1, y1, target)
        results.append([mae, mse, r2])

    for i in range(len(results)):
        print(f'Target: {output_vars[i]}:')
        print(f'    Mean abs error:    {results[i][0]}')
        print(f'    Mean error: {results[i][1]}')
        print(f'    R2_score: {results[i][2]}')

    flag = False
    if flag:
        # Upload the trained model
        os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'

        #https://colab.research.google.com/github/tensorflow/docs/blob/master/site/en/guide/checkpoint.ipynb?hl=es-MX#scrollTo=LeDp7dovcbus
        #https://colab.research.google.com/github/tensorflow/docs/blob/master/site/en/guide/saved_model.ipynb?hl=es-MX#scrollTo=85PUO9iWH7xn

        strategy = 0; target = 'dyn_instr_count'; y1 = dataset[target].values; y1 = y1[x1[:, -2] == strategy]
        x_grid = x1[x1[:, -2] == strategy]; x_grid = np.delete(x_grid, np.s_[-2:-1], axis=1)
        mae, mse, r2_score = nn_model(x_grid, y1, target)

        y = np.stack((dataset.loc[:, output_vars].values))


if __name__ == '__main__':

    run()

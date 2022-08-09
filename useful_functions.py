import matplotlib.pyplot as plt


def plot_tot_bar_results(data, 
                         order_by, 
                         title,
                         ins_ytick_space=3000,
                         nr_low_lang=5,
                         inset=False,
                         hline=True):
    """This function visualizes the results of  as bar plots
       Args:
       data: dataframe (typically index is languages - to be used as label for x-axis)
       order_by: string- column name to be ordered in descending order
       title: string- plot title
       hline: if True an horizontal line is plotted at height 100000
       ins_ytick_space: the spacing of the vertical ticks in the inset
       nr_low_lang: number of least represented languages to show in the inset( it assumes that the numbers 
       in the dataframe are in descending order)
     """
    fig,axes= plt.subplots(1,1,figsize=(10, 5))
    axes.set_title(f"{title}",fontsize=20)
    axes.set_ylabel(f'# tagged metafields', fontsize=20 )
    if hline:
        axes.hlines(1e5, -1, 100,color='black',lw=3, label='100000 fields')
    sorted_data=data.sort_values(by=order_by, ascending=False)
    sorted_data.plot( kind='bar', mark_right=True,ax=axes)
    axes.tick_params(axis='both', which='major', labelsize=25) # setting tick parameter of inset plot
    axes.legend(loc='best',fontsize=20)
    if inset:
    ################### inset plot
        left, bottom, width, height = [0.6, 0.3, 0.2, 0.3] #position inset within main plot
        ax2 = fig.add_axes([left, bottom, width, height])
        sorted_data.iloc[-nr_low_lang:].plot( kind='bar', mark_right=True,legend=False, ax=ax2)
        ax2.set_ylabel(f'# tagged metafields', fontsize=10 )
        df_low_languages=sorted_data.iloc[-nr_low_lang:]
        #note: this works well if all values to represent in the inset are positive, 
        # to do: implement for mized cases
        ax2.set_yticks([ i for i in range (0,int(df_low_languages.iloc[0]),ins_ytick_space)])
        ax2.tick_params(axis='both', which='major', labelsize=15) # setting tick parameter of inset plot
    return None
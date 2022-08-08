
import matplotlib.pyplot as plt

def plot_tot_bar_results(data, title, hline=True, inset_yticks_spacing=30000,nr_low_languages=5):
    """This function visualizes the results of  as bar plots
       Args:
       data: dataframe (typically index is languages and a column with nummber of segments)
             NOTE: to work will the numbers should be organizied in descending order (for the relevant column)
       hline: if True an horizontal line is plotted at height 100000
       inset_yticks_spacing: the spacing of the vertical ticks in the inset
       nr_low_languages: number of least represented languages to show in the inset( it assumes that the numbers 
       in the dataframe are in descending order)
     """
    fig,axes= plt.subplots(1,1,figsize=(10, 5))
    axes.set_title(f"{title}",fontsize=20)
    axes.set_ylabel(f'# tagged metafields', fontsize=20 )
    if hline:
        axes.hlines(1e5, -1, 100,color='black',lw=3, label='100000 fields')
    data.plot( kind='bar', mark_right=True,ax=axes)
    axes.tick_params(axis='both', which='major', labelsize=25) # setting tick parameter of inset plot
    ################### inset plot
    left, bottom, width, height = [0.6, 0.3, 0.2, 0.3] #position inset within main plot
    ax2 = fig.add_axes([left, bottom, width, height])
    data.iloc[-nr_low_languages:].plot( kind='bar', mark_right=True,legend=False, ax=ax2)
    ax2.set_ylabel(f'# tagged metafields', fontsize=10 )
    df_low_languages=data.iloc[-nr_low_languages:]
    ax2.set_yticks([ i for i in range (0,int(df_low_languages.iloc[0]),inset_yticks_spacing)])
    ax2.tick_params(axis='both', which='major', labelsize=15) # setting tick parameter of inset plot
    axes.legend(loc='best',fontsize=20)
    return None
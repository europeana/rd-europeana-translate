
import matplotlib.pyplot as plt


def plot_tot_bar_results(data, title, hline=True, inset=False, lst=[0,100,1000],nr_low_languages=5):
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
    data.plot( kind='bar', logy=True, mark_right=True,ax=axes)
    axes.tick_params(axis='both', which='major', labelsize=20) # setting tick parameter of inset plot
    ################### inset plot
    if inset:
        left, bottom, width, height = [0.6, 0.3, 0.2, 0.3] #position inset within main plot
        ax2 = fig.add_axes([left, bottom, width, height])
        data.iloc[-nr_low_languages:].plot( kind='bar', mark_right=True,legend=False, ax=ax2)
        ax2.set_ylabel(f'# tagged metafields', fontsize=10 )
        df_low_languages=data.iloc[-nr_low_languages:]
        ax2.set_yticks(lst)
        # ax2.set_yticks([ i for i in range (0,int(df_low_languages.iloc[0]),inset_yticks_spacing)])
        ax2.tick_params(axis='both', which='major', labelsize=15) # setting tick parameter of inset plot
        axes.legend(loc='best',fontsize=20)
    return None



# The followign two functions are focused on plotting vertical or horizontal bars in semilog scale and have the possibility to print 
# number on top of the bars. 
#At the same time do not have the possibility to show insets.



def plot_tot_barh_numbers(data, title,name_file='na', x_lim=0.4e8,slide_label=1,segments_label=False,save=False):
    """This function visualizes the results of  as bar horizontal bar plots
       Args:
       data: dataframe (typically index is languages and a column with nummber of segments)
             NOTE: to work the numbers should be organizied in descending order (for the relevant column)
       title: plot title
       x_lim: right limit for the x axis
       segment_label: if True the numbers of segments are printed on top of the bars
     """
    fig,axes= plt.subplots(1,1,dpi=150)
    axes.set_title(f"{title}")#fontsize=20
    axes.set_xlabel(f'Amount of tagged fields')
    axes.set_ylabel(f'Languages')
    data.plot(kind='barh',logx=True, mark_right=True,ax=axes, color='red') #'#5F9EA0'
    if segments_label:
        for p in axes.patches: # This part is to add the number of sesgments on top of the bars
            axes.annotate('{:,}'.format(p.get_width()), ( 3.5e7*slide_label,  p.get_y()+0.09),fontsize=6)#fontsize=20
    axes.tick_params(axis='both', which='major') # setting tick parameter of inset plot #labelsize=14
    axes.set_xlim(0.5,x_lim )
    if save:
        fig.savefig(f'{name_file}.png',dpi=400)

    return None

def plot_tot_barv_numbers(data, title, y_lim=1e7, segments_label=False):
    """This function visualizes the results of  as vertical bar plots
       Args:
       data: dataframe (typically index is languages and a column with number of segments)
             NOTE: to work  the numbers should be organizied in descending order (for the relevant column)
       title: plot title
       x_lim: right limit for the x axis
       segment_label: if True the numbers of segments are printed on top of the bars
     """
    fig,axes= plt.subplots(1,1,figsize=(10,5))
    axes.set_title(f"{title}",fontsize=20)
    axes.set_ylabel(f'Amount of tagged fields', fontsize=20 )
    data.plot(kind='bar',logy=True, mark_right=True,ax=axes)
    if segments_label:
        for p in axes.patches: # This part is to add the number of sesgments on top of the bars
            axes.annotate(str(p.get_height()), ( p.get_x(),  p.get_height()*1.8),fontsize=15,rotation=90)
    axes.tick_params(axis='both', which='major', labelsize=20) # setting tick parameter of inset plot
    axes.set_ylim(1,y_lim )

    return None



def plot_multiple_hor_bar(df,columns,display_values, title, x_label, y_label, x=0.8e7, dpi=150, segments_label=False):
    """ This function plots multiple horizontal bars
    df: dataframe where the data are stored
    columns: which columns to plot as bars
    display_values: from which column to print on plot (if segments_label is True)
    title: title of plot
    x_label: x label
    y_label: y label
    x: x value where to start printing on plot
    dpi: dpi value for the plot
    segments_label: if True values are printed on plot
    """
    fig,axes= plt.subplots(1,1,dpi=dpi)
    axes.set_title(f'{title}')#fontsize=20
    axes.set_xlabel(f'{x_label}')
    axes.set_ylabel(f'{y_label}')
    df[columns].plot(kind='barh',logx=True,ax=axes, mark_right=True, legend=False) 
    if segments_label: # in case true the values are printed on plot
        for c, i in zip(df[f'{display_values}'].values, axes.patches):
            axes.text(x,i.get_y(),str(c.round(2))) 
    return None


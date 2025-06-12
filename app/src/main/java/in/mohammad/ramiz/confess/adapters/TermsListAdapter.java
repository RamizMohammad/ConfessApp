package in.mohammad.ramiz.confess.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.entityfiles.ListEntites;

public class TermsListAdapter extends ArrayAdapter<ListEntites> {

    public TermsListAdapter(ArrayList<ListEntites> items, Context context) {
        super(context, R.layout.term_layout_file, items);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ListEntites item = getItem(position);
        ViewHolder viewHolder;

        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = inflater.inflate(R.layout.term_layout_file, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.heading = convertView.findViewById(R.id.heading);
            viewHolder.terms = convertView.findViewById(R.id.termsPlaceholder);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        if (item.isHeading()) {
            viewHolder.heading.setVisibility(View.VISIBLE);
            viewHolder.terms.setVisibility(View.GONE);
            viewHolder.heading.setText(item.getText());
        } else {
            viewHolder.heading.setVisibility(View.GONE);
            viewHolder.terms.setVisibility(View.VISIBLE);
            viewHolder.terms.setText(item.getText());
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView heading;
        TextView terms;
    }
}

package de.danoeh.antennapod.ui.home.sections;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.databinding.HomeSectionEchoBinding;
import de.danoeh.antennapod.ui.echo.EchoActivity;
import de.danoeh.antennapod.ui.home.HomeFragment;

public class EchoSection extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        HomeSectionEchoBinding viewBinding = HomeSectionEchoBinding.inflate(inflater);
        viewBinding.echoButton.setOnClickListener(v -> startActivity(new Intent(getContext(), EchoActivity.class)));
        viewBinding.closeButton.setOnClickListener(v -> {
            getContext().getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(HomeFragment.PREF_HIDE_ECHO, 2023).apply();
            ((MainActivity) getActivity()).loadFragment(HomeFragment.TAG, null);
        });
        return viewBinding.getRoot();
    }
}

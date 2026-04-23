package com.ece420.lab6;

import java.util.ArrayList;
import java.util.Arrays;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
public class FisherClassifier {
    public FisherClassifier() {}

    public static int[] argsort(final double[] a, final boolean ascending) {
        Integer[] indexes = new Integer[a.length];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, new Comparator<Integer>() {
            @Override
            public int compare(final Integer i1, final Integer i2) {
                return (ascending ? 1 : -1) * Double.compare(a[i1], a[i2]);
            }
        });
        return asArray(indexes);
    }


    public Matrix GetEigenfaces(Matrix A, int num_components) {
        Matrix A_transpose = A.transpose();
        // Then compute the L matrix
        Matrix L = A_transpose.times(A);
        EigenvalueDecomposition decomposition = L.eig();
        // We are a real matrix so surely we only have real eigenvalues
        double[] eigenvalues = decomposition.getRealEigenvalues();
        Matrix V = decomposition.getV();
        // Sort it in descending value
        int[] indices = argsort(eigenvalues, false);
        // Then we can get our eigenvectors matrix
        // Construct the eigenvector_L matrix
        int N = A.getColumnDimension();
        int M = A.getRowDimension();
        int actualComponents = Math.min(num_components, N);

        for (int i = 0; i < actualComponents; i++) {
            int sortedIdx = indices[i];

            // Get the i-th eigenvector from the decomposition (column vector)
            Matrix vi = V.getMatrix(0, N - 1, sortedIdx, sortedIdx);

            // Project into high-dimensional space: ui = A * vi
            Matrix ui = A.times(vi);

            // Normalize the eigenface
            double norm = ui.norm2();
            if (norm > 1e-8) {
                ui = ui.times(1.0 / norm);
            }

            // Store in the resulting matrix
            eigenfaces.setMatrix(0, M - 1, i, i, ui);
        }

        return eigenfaces;
    }

    public Matrix GetFisherFaces(Matrix A, int[] labels, int num_components) {
        Matrix W_pca = GetEigenfaces(A, num_components);
        Matrix X_pca = A.times(W_pca);

        int N = X_pca.getRowDimension();
        int M = X_pca.getColumnDimension();

        double[] mean = new double[M];
        for (int i = 0; i < M; i++) {
            double sum = 0;
            for (int j = 0; j < N; j++) {
                sum += X_pca.get(j, i);
            }
            mean[i] = sum / N;
        }

        Matrix meanTotal = new Matrix(mean, 1);

        int[] classes = Arrays.stream(labels).distinct().toArray();

        Matrix Sw = new Matrix(M, M);
        Matrix Sb = new Matrix(M, M);
        for (int cls : classes) {
            // Filter rows belonging to class c
            ArrayList<Integer> classIndices = new ArrayList<>();
            for (int i = 0; i < labels.length; i++) {
                if (labels[i] == c) classIndices.add(i);
            }

            int n_c = classIndices.size();
            Matrix X_c = new Matrix(n_c, K);
            for (int i = 0; i < n_c; i++) {
                X_c.setMatrix(i, i, 0, K - 1, X_pca.getMatrix(classIndices.get(i), classIndices.get(i), 0, K - 1));
            }

            // Mean of class c
            double[] meanCArr = new double[K];
            for (int j = 0; j < K; j++) {
                double sum = 0;
                for (int i = 0; i < n_c; i++) sum += X_c.get(i, j);
                meanCArr[j] = sum / n_c;
            }
            Matrix meanC = new Matrix(meanCArr, 1);

            // Within-class scatter: Sw += (X_c - mean_c)^T * (X_c - mean_c)
            for (int i = 0; i < n_c; i++) {
                Matrix row = X_c.getMatrix(i, i, 0, K - 1);
                Matrix diff = row.minus(meanC);
                Sw = Sw.plus(diff.transpose().times(diff));
            }

            // Between-class scatter: Sb += n_c * (mean_c - mean_total)^T * (mean_c - mean_total)
            Matrix meanDiff = meanC.minus(meanTotal);
            Sb = Sb.plus(meanDiff.transpose().times(meanDiff).times(n_c));
        }

        Matrix J = Sw.inverse().times(Sb);
        EigenvalueDecomposition ldaDecomp = J.eig();

        double[] ldaEigenvalues = ldaDecomp.getRealEigenvalues();
        int[] ldaIndices = argsort(ldaEigenvalues, false);
        Matrix V_lda = ldaDecomp.getV();

        // 5. Sort and select LDA components (C-1 components max)
        int num_lda_components = classes.length - 1;
        Matrix W_lda = new Matrix(K, num_lda_components);
        for (int i = 0; i < num_lda_components; i++) {
            W_lda.setMatrix(0, K - 1, i, i, V_lda.getMatrix(0, K - 1, ldaIndices[i], ldaIndices[i]));
        }
        return W_pca.times(W_lda);
    }

    public void ComputeTrainingWeights() {
        // We need to populate the face datas first
    }
}

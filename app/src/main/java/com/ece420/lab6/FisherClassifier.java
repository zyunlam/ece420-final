package com.ece420.lab6;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;

public class FisherClassifier {
    private Matrix fisherfaces;
    private Matrix A;

    private Matrix training_weights;
    private double[] meanFace;
    Map<Integer, double[]> classWeights;
    private double[][] trainingWeightArray;  // each training image in Fisher space
    private int[] trainingLabels;            // label per training image
    private int[] targetSize;
    private int[] validLabels;

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
        int[] return_value = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            return_value[i] = indexes[i];
        }
        return return_value;
    }

    public Matrix GetEigenfaces(Matrix A, int num_components) {
        Matrix A_transpose = A.transpose();
        Matrix L = A_transpose.times(A);
        EigenvalueDecomposition decomposition = L.eig();
        double[] eigenvalues = decomposition.getRealEigenvalues();
        Matrix V = decomposition.getV();
        int[] indices = argsort(eigenvalues, false);

        int N = A.getColumnDimension();
        int M = A.getRowDimension();
        int actualComponents = Math.min(num_components, N);

        Matrix eigenfaces = new Matrix(M, actualComponents);

        for (int i = 0; i < actualComponents; i++) {
            int sortedIdx = indices[i];
            Matrix vi = V.getMatrix(0, N - 1, sortedIdx, sortedIdx);
            Matrix ui = A.times(vi);
            double norm = ui.norm2();
            if (norm > 1e-8) {
                ui = ui.times(1.0 / norm);
            }
            eigenfaces.setMatrix(0, M - 1, i, i, ui);
        }

        return eigenfaces;
    }

    public Matrix GetFisherFaces(Matrix A, int[] labels, int num_components) {
        Matrix W_pca = GetEigenfaces(A, num_components);
        Matrix X_pca = A.transpose().times(W_pca);

        int N = X_pca.getRowDimension();
        int M = X_pca.getColumnDimension();
        int K = M;

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

        Log.d("FisherClassifier", "Classes: " + classes.length);
        Matrix Sw = new Matrix(M, M);
        Matrix Sb = new Matrix(M, M);
        for (int c : classes) {
            ArrayList<Integer> classIndices = new ArrayList<>();
            for (int i = 0; i < labels.length; i++) {
                if (labels[i] == c) classIndices.add(i);
            }

            int n_c = classIndices.size();
            Matrix X_c = new Matrix(n_c, K);
            for (int i = 0; i < n_c; i++) {
                X_c.setMatrix(i, i, 0, K - 1, X_pca.getMatrix(classIndices.get(i), classIndices.get(i), 0, K - 1));
            }

            double[] meanCArr = new double[K];
            for (int j = 0; j < K; j++) {
                double sum = 0;
                for (int i = 0; i < n_c; i++) sum += X_c.get(i, j);
                meanCArr[j] = sum / n_c;
            }
            Matrix meanC = new Matrix(meanCArr, 1);

            for (int i = 0; i < n_c; i++) {
                Matrix row = X_c.getMatrix(i, i, 0, K - 1);
                Matrix diff = row.minus(meanC);
                Sw = Sw.plus(diff.transpose().times(diff));
            }

            Matrix meanDiff = meanC.minus(meanTotal);
            Sb = Sb.plus(meanDiff.transpose().times(meanDiff).times(n_c));
        }

        Matrix J = Sw.inverse().times(Sb);
        EigenvalueDecomposition ldaDecomp = J.eig();

        double[] ldaEigenvalues = ldaDecomp.getRealEigenvalues();
        int[] ldaIndices = argsort(ldaEigenvalues, false);
        Matrix V_lda = ldaDecomp.getV();

        int num_lda_components = classes.length - 1;
        Matrix W_lda = new Matrix(K, num_lda_components);
        for (int i = 0; i < num_lda_components; i++) {
            W_lda.setMatrix(0, K - 1, i, i, V_lda.getMatrix(0, K - 1, ldaIndices[i], ldaIndices[i]));
        }
        return W_pca.times(W_lda);
    }

    public Map<Integer, double[]> computeClassAverages(Matrix trainingWeights, int[] labels) {
        Map<Integer, List<double[]>> groupedWeights = new HashMap<>();

        int numImages = trainingWeights.getRowDimension();
        int numFeatures = trainingWeights.getColumnDimension();

        for (int i = 0; i < numImages; i++) {
            int label = labels[i];
            if (!groupedWeights.containsKey(label)) {
                groupedWeights.put(label, new ArrayList<>());
            }
            groupedWeights.get(label).add(trainingWeights.getMatrix(i, i, 0, numFeatures - 1).getRowPackedCopy());
        }

        Map<Integer, double[]> classAverages = new HashMap<>();

        for (Integer label : groupedWeights.keySet()) {
            List<double[]> weights = groupedWeights.get(label);
            double[] meanVector = new double[numFeatures];

            for (double[] w : weights) {
                for (int f = 0; f < numFeatures; f++) {
                    meanVector[f] += w[f];
                }
            }

            for (int f = 0; f < numFeatures; f++) {
                meanVector[f] /= weights.size();
            }

            classAverages.put(label, meanVector);
        }

        return classAverages;
    }

    public void ComputeTrainingWeights(double[][] imageList, int[] labels, int width, int height) {
        Log.d("FisherClassifier", "Images: " + imageList.length);
        int numImages = imageList.length;
        int numPixels = imageList[0].length;

        this.targetSize = new int[]{width, height};
        this.validLabels = labels;
        this.meanFace = new double[numPixels];

        for (int p = 0; p < numPixels; p++) {
            double sum = 0;
            for (int i = 0; i < numImages; i++) {
                sum += imageList[i][p];
            }
            this.meanFace[p] = sum / numImages;
        }

        this.A = new Matrix(numPixels, numImages);

        for (int i = 0; i < numImages; i++) {
            for (int p = 0; p < numPixels; p++) {
                double centeredValue = imageList[i][p] - this.meanFace[p];
                this.A.set(p, i, centeredValue);
            }
        }

        this.fisherfaces = GetFisherFaces(A, labels, 40);
        this.training_weights = A.transpose().times(fisherfaces);
        this.trainingWeightArray = training_weights.getArray();
        this.trainingLabels = labels;

        // not used in current implementation, but in original proposal
        this.classWeights = computeClassAverages(this.training_weights, labels);
    }

    public ClassifierResult ClassifyFace(double[] flatImage, double threshold) {
        if (meanFace == null || fisherfaces == null) {
            return new ClassifierResult(-1, Double.MAX_VALUE);
        }

        int numPixels = flatImage.length;
        double[] phiNewArr = new double[numPixels];
        for (int i = 0; i < numPixels; i++) {
            phiNewArr[i] = flatImage[i] - meanFace[i];
        }

        Matrix phiNew = new Matrix(phiNewArr, 1);
        Matrix omegaNew = phiNew.times(fisherfaces);
        double[] omegaNewArr = omegaNew.getRowPackedCopy();

        // Build sorted list of all class distances
        List<ClassifierResult> allResults = new ArrayList<>();
        Map<Integer, Integer> voteCount = new HashMap<>();
        for (int i = 0; i < trainingWeightArray.length; i++) {
            double[] trainVec = trainingWeightArray[i];

            double distance = 0;
            for (int j = 0; j < omegaNewArr.length; j++) {
                double diff = omegaNewArr[j] - trainVec[j];
                distance += diff * diff;
            }
            distance = Math.sqrt(distance);

            int label = trainingLabels[i];

            allResults.add(new ClassifierResult(label, distance));
        }

        // Sort ascending by distance
        allResults.sort((a, b) -> Double.compare(a.getDistance(), b.getDistance()));

        // Take top 10
        List<ClassifierResult> topK = new ArrayList<>(
                allResults.subList(0, Math.min(10, allResults.size()))
        );
        
        // Majority voting
        Map<Integer, Integer> votes = new HashMap<>();
        for (ClassifierResult r : topK) {
            int label = r.getIndex();
            votes.put(label, votes.getOrDefault(label, 0) + 1);
        }

        // Find winner
        int bestLabel = -1;
        int maxVotes = -1;
        for (Map.Entry<Integer, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                bestLabel = entry.getKey();
            }
        }
        ClassifierResult closest = topK.get(0);
        return new ClassifierResult(bestLabel, closest.getDistance(), new ArrayList<>(topK));
    }
}
package com.ece420.lab6;

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

    private int[] targetSize;
    private int[] validLabels;

    public FisherClassifier() {}

    // we should also have the option to load the fisher faces from the disk

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

        Matrix eigenfaces = new Matrix(M, actualComponents);

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

        Matrix Sw = new Matrix(M, M);
        Matrix Sb = new Matrix(M, M);
        for (int c : classes) {
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

    public Map<Integer, double[]> computeClassAverages(Matrix trainingWeights, int[] labels) {
        // 1. Group weight vectors by label
        Map<Integer, List<double[]>> groupedWeights = new HashMap<>();

        int numImages = trainingWeights.getRowDimension();
        int numFeatures = trainingWeights.getColumnDimension();

        for (int i = 0; i < numImages; i++) {
            int label = labels[i];
            if (!groupedWeights.containsKey(label)) {
                groupedWeights.put(label, new ArrayList<>());
            }
            // Extract the row as an array
            groupedWeights.get(label).add(trainingWeights.getMatrix(i, i, 0, numFeatures - 1).getRowPackedCopy());
        }

        // 2. Compute the mean for each group
        Map<Integer, double[]> classAverages = new HashMap<>();

        for (Integer label : groupedWeights.keySet()) {
            List<double[]> weights = groupedWeights.get(label);
            double[] meanVector = new double[numFeatures];

            for (double[] w : weights) {
                for (int f = 0; f < numFeatures; f++) {
                    meanVector[f] += w[f];
                }
            }

            // Divide by number of samples in this class
            for (int f = 0; f < numFeatures; f++) {
                meanVector[f] /= weights.size();
            }

            classAverages.put(label, meanVector);
        }

        return classAverages;
    }

    /**
     *
     * @param imageList List of images in a vector
     * @param labels Labels of each image and stuff
     * @param width int width, like 128
     * @param height 128 as well. This should be the same as whatever the matrix is above and stuff
     */
    public void ComputeTrainingWeights(double[][] imageList, int[] labels, int width, int height) {
        // We need to populate the face data first
        int numImages = imageList.length;
        int numPixels = imageList[0].length;

        this.targetSize = new int[]{width, height};
        this.validLabels = labels;
        this.meanFace = new double[numPixels];

        // 1. Calculate the mean face (Average of all images)
        for (int p = 0; p < numPixels; p++) {
            double sum = 0;
            for (int i = 0; i < numImages; i++) {
                sum += imageList[i][p];
            }
            this.meanFace[p] = sum / numImages;
        }

        // 2. Create the centered data matrix A
        // We initialize A as (Pixels x Images) -> Column-major
        this.A = new Matrix(numPixels, numImages);

        for (int i = 0; i < numImages; i++) {
            for (int p = 0; p < numPixels; p++) {
                // Subtract the mean: Phi = Image - Mean
                double centeredValue = imageList[i][p] - this.meanFace[p];
                this.A.set(p, i, centeredValue);
            }
        }
        this.fisherfaces = GetFisherFaces(A, labels, 150);
        this.training_weights = A.times(fisherfaces);
        this.classWeights = computeClassAverages(this.training_weights, labels);

    }

    public ClassifierResult ClassifyFace(double[] flatImage, double threshold) {
        // in case training doesn't finish before classification
        if (meanFace == null || fisherfaces == null) {
            return new ClassifierResult(-1, Double.MAX_VALUE);
        }

        int numPixels = flatImage.length;

        // Subtract the mean face: phi_new = gamma_new - mean_face
        double[] phiNewArr = new double[numPixels];
        for (int i = 0; i < numPixels; i++) {
            phiNewArr[i] = flatImage[i] - meanFace[i];
        }
        // Create a 1 x Pixels matrix
        Matrix phiNew = new Matrix(phiNewArr, 1);

        // Project into Fisher space: omega_new = phi_new * fisherfaces
        // Result is a 1 x num_components matrix
        Matrix omegaNew = phiNew.times(fisherfaces);
        double[] omegaNewArr = omegaNew.getRowPackedCopy();

        int bestLabel = -1;
        double minDistance = Double.MAX_VALUE;

        // 3. Find the closest class weight (Nearest Neighbor)
        for (Map.Entry<Integer, double[]> entry : classWeights.entrySet()) {
            double[] avgWeight = entry.getValue();

            // Calculate Euclidean Distance (L2 Norm)
            double distance = 0;
            for (int i = 0; i < omegaNewArr.length; i++) {
                double diff = omegaNewArr[i] - avgWeight[i];
                distance += diff * diff;
            }
            distance = Math.sqrt(distance);

            if (distance < minDistance) {
                minDistance = distance;
                bestLabel = entry.getKey();
            }
        }

        return new ClassifierResult(bestLabel, minDistance);
    }
 }
